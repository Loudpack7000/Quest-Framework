# Quest Logs Directory

This folder contains automatic log files from the AI Quest System.

## New Features (FIXED!)

### ✅ **Automated Quest Execution Logs**
- Quest scripts now write debug logs directly to this folder
- **Cook's Assistant** automation logs are saved here with full debug information
- Real-time progress tracking during automated quest completion

### ✅ **Fixed Issues**
1. **Quest Script Logging Integration** - `Logger.log()` calls from quest scripts now save to files
2. **Grand Exchange Interface** - GE buying now works properly with fallback methods
3. **Enhanced Debug Output** - Detailed execution tracking for troubleshooting

## File Types

### Manual Recording Logs
- **QUEST_[QuestName]_YYYYMMDD_HHMMSS.log** - Manual quest recording sessions
- **QUEST_Free_Discovery_YYYYMMDD_HHMMSS.log** - Free discovery mode sessions

### Automated Quest Logs
- **QUEST_Cook_s_Assistant_[timestamp].log** - Automated Cook's Assistant execution logs
- Contains detailed debug info about:
  - Quest state detection
  - Item gathering progress
  - Grand Exchange transactions
  - Dialogue handling
  - Step-by-step execution

## How to Test Automation

### Running Cook's Assistant Automation:
1. Launch the script (`AI Quest Framework v7.0`)
2. In the GUI, select **"Cook's Assistant"** from the quest dropdown
3. Click **"START QUEST AUTOMATION"**
4. Check this folder for the new log file with detailed execution info

### Expected Log Content:
- Quest initialization and setup
- Config value monitoring (quest progress detection)
- Item gathering attempts (Grand Exchange purchasing)
- Dialogue interactions with NPCs
- Step completion confirmations
- Error handling and retry attempts

## Debugging Failed Runs

If automation fails, check the latest log file for:
- `❌` Error messages
- `🔧 DEBUG:` diagnostic information
- Grand Exchange transaction details
- Quest state detection values

## Log Format
- **[HH:MM:SS] CATEGORY: Message** - Timestamped entries
- **🎯, ✅, ❌, 🔧** - Visual indicators for different message types
- **SCRIPT:** lines show generated automation code
- **ACTION:** lines show player interactions captured

All logs include both console output and quest-specific debug information for complete troubleshooting capability.
