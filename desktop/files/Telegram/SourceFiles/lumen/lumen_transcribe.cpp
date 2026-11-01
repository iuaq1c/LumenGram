/*
This file is part of Lumengram, a modification of Telegram Desktop.

For license and copyright information please follow this link:
https://github.com/telegramdesktop/tdesktop/blob/master/LEGAL
*/
#include "lumen/lumen_transcribe.h"

#include "base/call_delayed.h"
#include "base/zlib_help.h"
#include "core/application.h"
#include "core/core_settings.h"
#include "core/file_location.h"
#include "data/data_document.h"
#include "data/data_document_media.h"
#include "data/data_file_origin.h"
#include "data/data_media_types.h"
#include "data/data_session.h"
#include "history/history.h"
#include "history/history_item.h"
#include "lang/lang_keys.h"
#include "main/main_session.h"
#include "media/audio/media_audio_ffmpeg_loader.h"
#include "ui/toast/toast.h"

#include <QtCore/QJsonDocument>
#include <QtCore/QJsonObject>
#include <QtNetwork/QNetworkAccessManager>
#include <QtNetwork/QNetworkReply>

#ifdef Q_OS_WIN
#include <windows.h>
#endif // Q_OS_WIN

namespace Lumen {
namespace {

constexpr auto kEnabledPref = "lumen/local_transcribe"_cs;
constexpr auto kModelRu = "vosk-model-small-ru-0.22"_cs;
constexpr auto kModelEn = "vosk-model-small-en-us-0.15"_cs;
constexpr auto kModelBaseUrl = "https://alphacephei.com/vosk/models/"_cs;
constexpr auto kMaxModelFileSize = 64 * 1024 * 1024;
constexpr auto kSampleRate = 16000.f;
constexpr auto kFileWaitTimeout = 60 * crl::time(1000);

// The Vosk speech engine is shipped as a DLL next to the app, so the build
// does not depend on it and the app works without it.
struct VoskApi {
	using ModelNew = void*(*)(const char*);
	using ModelFree = void(*)(void*);
	using RecognizerNew = void*(*)(void*, float);
	using RecognizerFree = void(*)(void*);
	using AcceptWaveformS = int(*)(void*, const short*, int);
	using Result = const char*(*)(void*);
	using SetLogLevel = void(*)(int);

	ModelNew modelNew = nullptr;
	ModelFree modelFree = nullptr;
	RecognizerNew recognizerNew = nullptr;
	RecognizerFree recognizerFree = nullptr;
	AcceptWaveformS acceptWaveformS = nullptr;
	Result result = nullptr;
	Result finalResult = nullptr;
	SetLogLevel setLogLevel = nullptr;
};

struct ModelState {
	QString name;
	void *model = nullptr;
	bool downloading = false;
	std::vector<Fn<void(void*)>> waiting;
};

[[nodiscard]] const VoskApi *LoadVosk() {
	static const auto result = []() -> std::optional<VoskApi> {
#ifdef Q_OS_WIN
		const auto path = QDir::toNativeSeparators(
			cExeDir() + u"vosk/libvosk.dll"_q);
		const auto module = LoadLibraryExW(
			reinterpret_cast<LPCWSTR>(path.utf16()),
			nullptr,
			LOAD_WITH_ALTERED_SEARCH_PATH);
		if (!module) {
			return std::nullopt;
		}
		auto api = VoskApi();
		const auto load = [&](auto &field, const char *name) {
			field = reinterpret_cast<std::decay_t<decltype(field)>>(
				GetProcAddress(module, name));
			return (field != nullptr);
		};
		if (!load(api.modelNew, "vosk_model_new")
			|| !load(api.modelFree, "vosk_model_free")
			|| !load(api.recognizerNew, "vosk_recognizer_new")
			|| !load(api.recognizerFree, "vosk_recognizer_free")
			|| !load(api.acceptWaveformS, "vosk_recognizer_accept_waveform_s")
			|| !load(api.result, "vosk_recognizer_result")
			|| !load(api.finalResult, "vosk_recognizer_final_result")
			|| !load(api.setLogLevel, "vosk_set_log_level")) {
			return std::nullopt;
		}
		api.setLogLevel(-1);
		return api;
#else // Q_OS_WIN
		return std::nullopt;
#endif // Q_OS_WIN
	}();
	return result ? &*result : nullptr;
}

[[nodiscard]] ModelState &Model() {
	static auto result = ModelState();
	return result;
}

[[nodiscard]] QString ModelName() {
	return Lang::LanguageIdOrDefault(Lang::Id()).startsWith(u"ru"_q)
		? kModelRu.utf16()
		: kModelEn.utf16();
}

[[nodiscard]] QString ModelsFolder() {
	return cWorkingDir() + u"tdata/lumen-speech/"_q;
}

// Vosk opens files with narrow paths, the short form keeps them ASCII.
[[nodiscard]] QByteArray NativeModelPath(const QString &folder) {
#ifdef Q_OS_WIN
	const auto native = QDir::toNativeSeparators(folder);
	auto buffer = std::array<wchar_t, MAX_PATH>();
	const auto length = GetShortPathNameW(
		reinterpret_cast<LPCWSTR>(native.utf16()),
		buffer.data(),
		buffer.size());
	if (length > 0 && length < buffer.size()) {
		return QString::fromWCharArray(buffer.data(), length).toLocal8Bit();
	}
	return native.toLocal8Bit();
#else // Q_OS_WIN
	return QFile::encodeName(folder);
#endif // Q_OS_WIN
}

[[nodiscard]] bool UnpackModel(const QByteArray &zipBytes, const QString &to) {
	auto zip = zlib::FileToRead(zipBytes);
	if (zip.goToFirstFile() != UNZ_OK) {
		return false;
	}
	const auto root = QDir::cleanPath(to);
	do {
		const auto name = zip.getCurrentFileName();
		const auto path = QDir::cleanPath(to + '/' + name);
		if (!path.startsWith(root + '/')) {
			return false;
		}
		if (name.endsWith('/')) {
			QDir().mkpath(path);
		} else {
			const auto content = zip.readCurrentFileContent(kMaxModelFileSize);
			if (zip.error() != UNZ_OK) {
				return false;
			}
			QDir().mkpath(QFileInfo(path).absolutePath());
			auto file = QFile(path);
			if (!file.open(QIODevice::WriteOnly)
				|| file.write(content) != content.size()) {
				return false;
			}
		}
		const auto jump = zip.goToNextFile();
		if (jump == UNZ_END_OF_LIST_OF_FILE) {
			break;
		} else if (jump != UNZ_OK) {
			return false;
		}
	} while (true);
	return true;
}

void FinishModelLoad(void *model) {
	auto &state = Model();
	state.model = model;
	state.downloading = false;
	for (const auto &callback : base::take(state.waiting)) {
		callback(model);
	}
}

void OpenModel(const QString &folder) {
	const auto api = LoadVosk();
	const auto path = NativeModelPath(folder);
	crl::async([=] {
		const auto model = api->modelNew(path.constData());
		crl::on_main([=] { FinishModelLoad(model); });
	});
}

void DownloadModel(const QString &name) {
	Ui::Toast::Show(tr::lng_lumen_transcribe_downloading(tr::now));
	static auto manager = QNetworkAccessManager();
	auto request = QNetworkRequest(QUrl(kModelBaseUrl.utf16() + name + u".zip"_q));
	request.setAttribute(
		QNetworkRequest::RedirectPolicyAttribute,
		QNetworkRequest::NoLessSafeRedirectPolicy);
	const auto reply = manager.get(request);
	QObject::connect(reply, &QNetworkReply::finished, [=] {
		reply->deleteLater();
		const auto bytes = (reply->error() == QNetworkReply::NoError)
			? reply->readAll()
			: QByteArray();
		const auto folder = ModelsFolder();
		crl::async([=] {
			const auto unpacked = !bytes.isEmpty()
				&& UnpackModel(bytes, folder);
			crl::on_main([=] {
				if (unpacked) {
					OpenModel(folder + name);
				} else {
					FinishModelLoad(nullptr);
				}
			});
		});
	});
}

void WithModel(Fn<void(void*)> callback) {
	auto &state = Model();
	const auto name = ModelName();
	if (state.model && state.name == name) {
		callback(state.model);
		return;
	}
	state.waiting.push_back(std::move(callback));
	if (state.downloading) {
		return;
	}
	if (state.model) {
		LoadVosk()->modelFree(state.model);
		state.model = nullptr;
	}
	state.name = name;
	state.downloading = true;
	const auto folder = ModelsFolder() + name;
	if (QFileInfo::exists(folder + u"/am"_q)) {
		OpenModel(folder);
	} else {
		DownloadModel(name);
	}
}

// Decodes the audio track to 16-bit samples with the same decoder the
// player uses, mixed down to mono.
[[nodiscard]] std::vector<short> DecodeMono(
		const QString &path,
		const QByteArray &data,
		int *sampleRate) {
	auto loader = Media::FFMpegLoader(
		Core::FileLocation(path),
		data,
		bytes::vector());
	if (!loader.open(0)) {
		return {};
	}
	const auto frameSize = loader.sampleSize();
	const auto channels = frameSize / int(sizeof(short));
	if (channels < 1 || frameSize % int(sizeof(short))) {
		return {};
	}
	*sampleRate = loader.samplesFrequency();
	auto result = std::vector<short>();
	while (true) {
		const auto read = loader.readMore();
		if (const auto error = std::get_if<Media::AudioPlayerLoader::ReadError>(&read)) {
			using Error = Media::AudioPlayerLoader::ReadError;
			if (*error == Error::Retry
				|| *error == Error::RetryNotQueued
				|| *error == Error::Wait) {
				continue;
			}
			break;
		}
		const auto bytes = v::get<bytes::const_span>(read);
		const auto samples = reinterpret_cast<const short*>(bytes.data());
		const auto frames = int(bytes.size()) / frameSize;
		for (auto i = 0; i != frames; ++i) {
			auto sum = 0;
			for (auto c = 0; c != channels; ++c) {
				sum += samples[i * channels + c];
			}
			result.push_back(short(sum / channels));
		}
	}
	return result;
}

[[nodiscard]] QString TextFromResult(const char *json) {
	return QJsonDocument::fromJson(QByteArray(json)).object().value(
		u"text"_q).toString().trimmed();
}

[[nodiscard]] QString Recognize(
		void *model,
		const std::vector<short> &samples,
		int sampleRate) {
	constexpr auto kChunk = 8000;
	const auto api = LoadVosk();
	const auto recognizer = api->recognizerNew(model, float(sampleRate));
	if (!recognizer) {
		return QString();
	}
	auto parts = QStringList();
	for (auto i = 0; i < int(samples.size()); i += kChunk) {
		const auto count = std::min(kChunk, int(samples.size()) - i);
		if (api->acceptWaveformS(recognizer, samples.data() + i, count)) {
			parts.push_back(TextFromResult(api->result(recognizer)));
		}
	}
	parts.push_back(TextFromResult(api->finalResult(recognizer)));
	api->recognizerFree(recognizer);
	parts.removeAll(QString());
	auto text = parts.join(' ');
	if (!text.isEmpty()) {
		text[0] = text[0].toUpper();
	}
	return text;
}

void TranscribeFile(
		const QString &path,
		const QByteArray &data,
		Fn<void(QString)> done) {
	WithModel([=](void *model) {
		if (!model) {
			done(QString());
			return;
		}
		crl::async([=] {
			auto sampleRate = 0;
			const auto samples = DecodeMono(path, data, &sampleRate);
			const auto text = (samples.empty() || !sampleRate)
				? QString()
				: Recognize(model, samples, sampleRate);
			crl::on_main([=] { done(text); });
		});
	});
}

} // namespace

bool LocalTranscribeEnabled() {
	return Core::App().settings().readPref<bool>(kEnabledPref, true);
}

void SetLocalTranscribeEnabled(bool enabled) {
	Core::App().settings().writePref<bool>(kEnabledPref, enabled);
	Core::App().saveSettingsDelayed();
}

bool UseLocalTranscribe(not_null<Main::Session*> session) {
	return LocalTranscribeEnabled()
		&& !session->premium()
		&& (LoadVosk() != nullptr);
}

void TranscribeLocally(
		not_null<HistoryItem*> item,
		Fn<void(QString)> done) {
	const auto document = item->media()
		? item->media()->document()
		: nullptr;
	if (!document) {
		done(QString());
		return;
	}
	const auto media = document->createMediaView();
	const auto ready = [=] {
		return media->loaded() || !document->filepath(true).isEmpty();
	};
	const auto start = [=] {
		TranscribeFile(document->filepath(true), media->bytes(), done);
	};
	if (ready()) {
		start();
		return;
	}
	document->save(Data::FileOrigin(item->fullId()), QString());
	const auto lifetime = std::make_shared<rpl::lifetime>();
	const auto finished = std::make_shared<bool>(false);
	const auto finish = [=](bool loaded) {
		if (*finished) {
			return;
		}
		*finished = true;
		lifetime->destroy();
		if (loaded) {
			start();
		} else {
			done(QString());
		}
	};
	document->session().downloaderTaskFinished(
	) | rpl::filter(ready) | rpl::on_next([=] {
		finish(true);
	}, *lifetime);
	base::call_delayed(kFileWaitTimeout, [=] { finish(false); });
}

} // namespace Lumen
