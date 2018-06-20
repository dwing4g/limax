using System;
using System.Collections.Generic;
using limax.codec;
using limax.util;
namespace limax.endpoint.auanyviews
{
	public sealed partial class ServiceResult
	{
		override protected void onClose() {}
		override protected void onAttach(long sessionid) {}
		override protected void onDetach(long sessionid, byte reason) {}
		override protected void onOpen(ICollection<long> sessionids) {
            __ProtocolProcessManager.onResultViewOpen(this);
        }
	}
}
