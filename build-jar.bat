@echo off
echo ========================================
echo    AI Quest Framework v7.4 - Builder
echo ========================================
echo Script Name: "AI Quest Framework"
echo Author: Leone
echo Version: 7.4 (Unified Item Gathering + Vampire Slayer + Enhanced Logging)
echo Category: UTILITY
echo ========================================

echo [1/7] Cleaning old builds and removing old versions...
REM Clean local build files
if exist "target\classes" rmdir /s /q "target\classes"
if exist "target\*.jar" del /q "target\*.jar"

REM Remove ALL old versions from DreamBot Scripts folder
echo - Removing old versions from DreamBot...
if exist "C:\Users\Leone\DreamBot\Scripts\Quest_Action_Recorder.jar" (
    del "C:\Users\Leone\DreamBot\Scripts\Quest_Action_Recorder.jar"
    echo   [SUCCESS] Removed Quest_Action_Recorder.jar
)
if exist "C:\Users\Leone\DreamBot\Scripts\quest-bot-enhanced.jar" (
    del "C:\Users\Leone\DreamBot\Scripts\quest-bot-enhanced.jar"
    echo   [SUCCESS] Removed quest-bot-enhanced.jar
)
if exist "C:\Users\Leone\DreamBot\Scripts\AI_Quest_System.jar" (
    del "C:\Users\Leone\DreamBot\Scripts\AI_Quest_System.jar"
    echo   [SUCCESS] Removed AI_Quest_System.jar
)
if exist "C:\Users\Leone\DreamBot\Scripts\AI_Quest_Framework_v7.4.jar" (
    del "C:\Users\Leone\DreamBot\Scripts\AI_Quest_Framework_v7.4.jar"
    echo   [SUCCESS] Removed previous AI_Quest_Framework_v7.4.jar
)
echo - Old versions cleaned up

echo [2/7] Creating directories...
if not exist "target\classes" mkdir "target\classes"
if not exist "quest_logs" mkdir "quest_logs"
if not exist "C:\Users\Leone\DreamBot\Scripts" (
    echo Creating DreamBot Scripts directory...
    mkdir "C:\Users\Leone\DreamBot\Scripts"
)

echo [2.5/7] Validating source files...
if not exist "src\main\java\quest\utils\DialogueUtil.java" (
    echo [ERROR] DialogueUtil.java not found!
    pause
    exit /b 1
)
if not exist "src\main\java\quest\utils\GrandExchangeUtil.java" (
    echo [ERROR] GrandExchangeUtil.java not found!
    pause
    exit /b 1
)
if not exist "src\main\java\quest\utils\QuestData.java" (
    echo [ERROR] QuestData.java not found!
    pause
    exit /b 1
)
if not exist "src\main\java\quest\utils\QuestLogger.java" (
    echo [ERROR] QuestLogger.java not found!
    pause
    exit /b 1
)
if not exist "src\main\java\quest\utils\ItemGatheringUtil.java" (
    echo [ERROR] ItemGatheringUtil.java not found!
    pause
    exit /b 1
)
if not exist "src\main\java\quest\core\QuestDatabase.java" (
    echo [ERROR] QuestDatabase.java not found!
    pause
    exit /b 1
)
if not exist "src\main\java\quest\core\QuestScript.java" (
    echo [ERROR] QuestScript.java not found!
    pause
    exit /b 1
)
if not exist "src\main\java\quest\core\QuestExecutor.java" (
    echo [ERROR] QuestExecutor.java not found!
    pause
    exit /b 1
)
if not exist "src\main\java\quest\gui\QuestSelectionGUI.java" (
    echo [ERROR] QuestSelectionGUI.java not found!
    pause
    exit /b 1
)
if not exist "src\main\java\quest\core\QuestEventLogger.java" (
    echo [ERROR] QuestEventLogger.java not found!
    pause
    exit /b 1
)
if not exist "src\main\java\quest\quests\CooksAssistantScript.java" (
    echo [ERROR] CooksAssistantScript.java not found!
    pause
    exit /b 1
)
if not exist "src\main\java\quest\quests\LemonTutQuest.java" (
    echo [ERROR] LemonTutQuest.java not found!
    pause
    exit /b 1
)
if not exist "src\main\java\quest\quests\VampireSlayerScript.java" (
    echo [ERROR] VampireSlayerScript.java not found!
    pause
    exit /b 1
)
if not exist "src\main\java\quest\quests\RuneMysteriesScript.java" (
    echo [ERROR] RuneMysteriesScript.java not found!
    pause
    exit /b 1
)
if not exist "src\main\java\quest\SimpleQuestBot.java" (
    echo [ERROR] SimpleQuestBot.java not found!
    pause
    exit /b 1
)
echo [SUCCESS] All source files found

echo [3/7] Checking dependencies...
if not exist "lib\client.jar" (
    echo [ERROR] lib\client.jar not found!
    pause
    exit /b 1
)
if not exist "lib\json.jar" (
    echo [ERROR] lib\json.jar not found!
    pause
    exit /b 1
)
echo [SUCCESS] All dependencies found

echo [4/7] Compiling all Java files...

echo - Compiling utility classes first...
javac -cp "lib\*" -d "target\classes" "src\main\java\quest\utils\DialogueUtil.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\utils\GrandExchangeUtil.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\utils\QuestData.java"
if %errorlevel% neq 0 goto :compile_error

echo - Compiling QuestLogger.java (Logging Utility)...
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\utils\QuestLogger.java"
if %errorlevel% neq 0 goto :compile_error

echo - Compiling ItemGatheringUtil.java (Unified Item Management)...
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\utils\ItemGatheringUtil.java"
if %errorlevel% neq 0 goto :compile_error

echo - Compiling core classes (part 1)...
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\core\QuestDatabase.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\core\QuestScript.java"
if %errorlevel% neq 0 goto :compile_error

echo - Compiling logging system...
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\core\QuestEventLogger.java"
if %errorlevel% neq 0 goto :compile_error

echo - Compiling quest implementations...
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\quests\LemonTutQuest.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\quests\CooksAssistantScript.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\quests\VampireSlayerScript.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\quests\RuneMysteriesScript.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\quests\ImpCatcherScript.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\quests\WitchsPotionScript.java"
if %errorlevel% neq 0 goto :compile_error

echo - Compiling quest executor...
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\core\QuestExecutor.java"
if %errorlevel% neq 0 goto :compile_error

echo - Compiling GUI (depends on QuestExecutor)...
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\gui\QuestSelectionGUI.java"
if %errorlevel% neq 0 goto :compile_error



echo - Compiling main script...
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\SimpleQuestBot.java"
if %errorlevel% neq 0 goto :compile_error

echo [SUCCESS] All Java files compiled successfully

echo [5/7] Creating JAR file...
cd target\classes
jar cf "..\AI_Quest_Framework_v7.4.jar" quest\*
cd ..\..

if not exist "target\AI_Quest_Framework_v7.4.jar" (
    echo [ERROR] JAR file creation failed!
    pause
    exit /b 1
)
echo [SUCCESS] JAR file created: AI_Quest_Framework_v7.4.jar

echo [6/7] Installing to DreamBot Scripts directory...
copy "target\AI_Quest_Framework_v7.4.jar" "C:\Users\Leone\DreamBot\Scripts\AI_Quest_Framework_v7.4.jar"
if %errorlevel% neq 0 (
    echo [ERROR] Failed to copy JAR to DreamBot Scripts directory!
    echo Please check if DreamBot directory exists: C:\Users\Leone\DreamBot\Scripts\
    pause
    exit /b 1
)
echo [SUCCESS] New version installed to DreamBot!

echo [7/7] Final verification...
if exist "C:\Users\Leone\DreamBot\Scripts\AI_Quest_Framework_v7.4.jar" (
    echo [SUCCESS] AI Quest Framework v7.4 is ready!
) else (
    echo [ERROR] Installation verification failed!
    pause
    exit /b 1
)

echo ========================================
echo [SUCCESS] AI QUEST FRAMEWORK v7.4 INSTALLED
echo ========================================
echo Script Location: C:\Users\Leone\DreamBot\Scripts\AI_Quest_Framework_v7.4.jar
echo Script Name: "AI Quest Framework v7.4"
echo Author: Leone
echo Version: 7.4 (Unified Item Gathering + Vampire Slayer + Enhanced Logging)
echo Category: UTILITY
echo ========================================
echo Features in v7.4:
echo - Universal DialogueUtil with DialogueStep pattern for complex dialogue sequences
echo - GrandExchangeUtil with intelligent price increase strategies (5%-50%)
echo - QuestData storage system for centralized quest information management
echo - Enhanced logging system with QuestLogger and QuestEventLogger
echo - Scalable architecture supporting dialogue, Grand Exchange, and item management
echo - Cook's Assistant and Vampire Slayer quest implementations
echo Quest logs saved to: quest_logs\
echo ========================================

echo.
echo [IMPORTANT NOTES]:
echo 1. RESTART DreamBot completely to clear script cache
echo 2. Look for "AI Quest Framework v7.4" in your scripts
echo 3. If still showing old version, click "Refresh Scripts"
echo.
echo Ready to test! The framework includes universal utilities for scalable automation.

echo [CLEANUP] Keeping target folder for debugging...
echo Target folder preserved at: target\
echo JAR file location: target\AI_Quest_Framework_v7.4.jar

pause
goto :end

:compile_error
echo [ERROR] Compilation failed!
echo Check the Java source files for syntax errors.
echo Make sure DreamBot client.jar is compatible with your code.
echo Check console output above for specific compilation errors.
pause
exit /b 1

:end
