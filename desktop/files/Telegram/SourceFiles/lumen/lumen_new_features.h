#pragma once

class HistoryItem;
namespace Main { class Session; }
namespace Window { class SessionController; }

namespace Lumen {

[[nodiscard]] bool FeatureEnabled(std::string_view key, bool fallback = false);
void SetFeatureEnabled(std::string_view key, bool enabled);
[[nodiscard]] QString FeatureText(std::string_view key);
void SetFeatureText(std::string_view key, const QString &value);
[[nodiscard]] int FeatureMode(std::string_view key);
void SetFeatureMode(std::string_view key, int value);
[[nodiscard]] rpl::producer<> FeatureChanges();
[[nodiscard]] rpl::producer<bool> FeatureEnabledValue(std::string key, bool fallback = false);
[[nodiscard]] int TransferRequestLimit(int standard, bool upload);
[[nodiscard]] int TransferSessionLimit(int standard, bool upload);
void SetupThreatProtection(not_null<Main::Session*> session);
[[nodiscard]] bool IsThreatHidden(not_null<const HistoryItem*> item);
void ShowThreatLog(not_null<Window::SessionController*> controller);

} // namespace Lumen
