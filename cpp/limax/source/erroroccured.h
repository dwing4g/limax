#pragma once

namespace limax {
	namespace erroroccured {

		void fireErrorOccured( EndpointManager* login, int type, int code, const std::string& info);

	} // namespace erroroccured {
} // namespace limax {

