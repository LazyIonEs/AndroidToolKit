"""Replay after baselineHotRun with -Pcompose.reload.devToolsHeadless=true.
Usage: python3 docs/migration/evidence/phase-07b/mcp/reproduce.py <JBR21_HOME>
"""
from pathlib import Path
import sys
import time
root = Path(__file__).resolve().parents[5]
sys.path.insert(0, str(root / "scripts/migration"))
original_sleep = time.sleep
time.sleep = lambda seconds: original_sleep(max(2, seconds))
import hot_mcp_client, hot_mcp_phase3
original_replay = hot_mcp_phase3.smoke_phase3
def replay(tool, output, root):
    def call(name, arguments=None):
        value = tool(name, arguments)
        if name == "restart":
            original_sleep(8)  # Orchestration can reconnect before the native window exists.
        return value
    return original_replay(call, output, root)
hot_mcp_phase3.smoke_phase3 = replay
sys.argv = [str(root / "scripts/migration/hot_mcp_client.py"), sys.argv[1], "--phase7b-smoke"]
hot_mcp_client.main()
