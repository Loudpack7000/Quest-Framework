@echo off
echo ========================================
echo    AI Quest Framework v7.6 - Builder
echo ========================================
echo Script Name: "AI Quest Framework"
echo Author: Leone
echo Version: 7.6 (Tree-Based Quest System + Romeo and Juliet + GE Integration)
echo Category: UTILITY
echo ========================================

REM Enable non-interactive mode when NONINTERACTIVE=1 is set in environment
if /i "%NONINTERACTIVE%"=="1" (
    set "SKIP_PAUSE=1"
    set "DEFAULT_CLEANUP_CHOICE=N"
echo [INFO] Non-interactive mode enabled (pauses suppressed)
)

echo [1/7] Cleaning old builds and removing old versions...
REM Clean local build files
if exist "target" rmdir /s /q "target"

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
if exist "C:\Users\Leone\DreamBot\Scripts\AI_Quest_Framework_v7.5.jar" (
    del "C:\Users\Leone\DreamBot\Scripts\AI_Quest_Framework_v7.5.jar"
    echo   [SUCCESS] Removed previous AI_Quest_Framework_v7.5.jar
)
if exist "C:\Users\Leone\DreamBot\Scripts\AI_Quest_Framework_v7.6.jar" (
    del "C:\Users\Leone\DreamBot\Scripts\AI_Quest_Framework_v7.6.jar"
    echo   [SUCCESS] Removed previous AI_Quest_Framework_v7.6.jar
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
    call :maybe_pause
    exit /b 1
)
if not exist "src\main\java\quest\utils\GrandExchangeUtil.java" (
    echo [ERROR] GrandExchangeUtil.java not found!
    call :maybe_pause
    exit /b 1
)
if not exist "src\main\java\quest\utils\QuestData.java" (
    echo [ERROR] QuestData.java not found!
    call :maybe_pause
    exit /b 1
)
if not exist "src\main\java\quest\utils\QuestLogger.java" (
    echo [ERROR] QuestLogger.java not found!
    call :maybe_pause
    exit /b 1
)
if not exist "src\main\java\quest\utils\ItemGatheringUtil.java" (
    echo [ERROR] ItemGatheringUtil.java not found!
    call :maybe_pause
    exit /b 1
)
if not exist "src\main\java\quest\core\QuestDatabase.java" (
    echo [ERROR] QuestDatabase.java not found!
    call :maybe_pause
    exit /b 1
)
if not exist "src\main\java\quest\core\QuestScript.java" (
    echo [ERROR] QuestScript.java not found!
    call :maybe_pause
    exit /b 1
)
if not exist "src\main\java\quest\core\QuestExecutor.java" (
    echo [ERROR] QuestExecutor.java not found!
    call :maybe_pause
    exit /b 1
)
if not exist "src\main\java\quest\core\QuestEventLogger.java" (
    echo [ERROR] QuestEventLogger.java not found!
    call :maybe_pause
    exit /b 1
)
if not exist "src\main\java\quest\gui\QuestSelectionGUI.java" (
    echo [ERROR] QuestSelectionGUI.java not found!
    call :maybe_pause
    exit /b 1
)



if not exist "src\main\java\quest\SimpleQuestBot.java" (
    echo [ERROR] SimpleQuestBot.java not found!
    call :maybe_pause
    exit /b 1
)
echo [SUCCESS] All source files found

echo [3/7] Checking dependencies...
if not exist "lib\client.jar" (
    echo [ERROR] lib\client.jar not found!
    call :maybe_pause
    exit /b 1
)
if not exist "lib\json.jar" (
    echo [ERROR] lib\json.jar not found!
    call :maybe_pause
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

echo - Compiling RunEnergyUtil.java (Run Energy Management)...
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\utils\RunEnergyUtil.java"
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



echo - Compiling Tree-Based Quest System...
echo   - Compiling core tree classes...
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\core\QuestNode.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\core\QuestTree.java"
if %errorlevel% neq 0 goto :compile_error

echo   - Compiling tree node classes...
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\nodes\ActionNode.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\nodes\DecisionNode.java"
if %errorlevel% neq 0 goto :compile_error

echo   - Compiling action nodes...
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\nodes\actions\TalkToNPCNode.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\nodes\actions\WalkToLocationNode.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\nodes\actions\InteractWithObjectNode.java"
if %errorlevel% neq 0 goto :compile_error

rem New action nodes
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\nodes\actions\TakeGroundItemNode.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\nodes\actions\EquipItemsNode.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\nodes\actions\UseItemOnObjectNode.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\nodes\actions\SelectDialogueOptionNode.java"
if %errorlevel% neq 0 goto :compile_error

rem New action node: UseItemOnNPCNode
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\nodes\actions\UseItemOnNPCNode.java"
if %errorlevel% neq 0 goto :compile_error


echo   - Compiling decision nodes...
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\nodes\decisions\QuestProgressDecisionNode.java"
if %errorlevel% neq 0 goto :compile_error

echo   - Compiling QuestTree base class...
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\core\QuestTree.java"
if %errorlevel% neq 0 goto :compile_error

echo   - Compiling quest trees...
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\trees\RomeoAndJulietTree.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\trees\RuneMysteriesTree.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\trees\CooksAssistantTree.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\trees\WitchsPotionTree.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\trees\SheepShearerTree.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\trees\VampyreSlayerTree.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\trees\DoricsQuestTree.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\trees\PiratesTreasureTree.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\trees\DemonSlayerTree.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\trees\GoblinDiplomacyTree.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\trees\XMarksTheSpotTree.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\trees\RestlessGhostTree.java"
if %errorlevel% neq 0 goto :compile_error

javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\trees\BlackKnightsFortressTree.java"
if %errorlevel% neq 0 goto :compile_error

rem New quest tree: The Corsair Curse
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\trees\CorsairCurseTree.java"
if %errorlevel% neq 0 goto :compile_error

rem New quest tree: Imp Catcher
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\trees\ImpCatcherTree.java"
if %errorlevel% neq 0 goto :compile_error

rem New quest tree: The Knight's Sword
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\trees\KnightsSwordTree.java"
if %errorlevel% neq 0 goto :compile_error

rem Prince Ali Rescue tree - TODO: Create implementation
rem javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\trees\PrinceAliRescueTree.java"
rem if %errorlevel% neq 0 goto :compile_error

rem New quest tree: Ernest the Chicken
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\trees\ErnestTheChickenTree.java"
if %errorlevel% neq 0 goto :compile_error

rem New quest tree: Below Ice Mountain
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\trees\BelowIceMountainTree.java"
if %errorlevel% neq 0 goto :compile_error

rem New quest tree: Dragon Slayer - Stage 1
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\trees\DragonSlayerTree.java"
if %errorlevel% neq 0 goto :compile_error

echo   - Compiling tree wrapper...
javac -cp "lib\*;target\classes" -d "target\classes" "src\main\java\quest\core\TreeQuestWrapper.java"
if %errorlevel% neq 0 goto :compile_error

echo - Compiling quest executor (depends on tree system)...
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
jar cf "..\AI_Quest_Framework_v7.6.jar" quest\*
cd ..\..

if not exist "target\AI_Quest_Framework_v7.6.jar" (
    echo [ERROR] JAR file creation failed!
    call :maybe_pause
    exit /b 1
)
echo [SUCCESS] JAR file created: AI_Quest_Framework_v7.6.jar

echo [6/7] Installing to DreamBot Scripts directory...
copy "target\AI_Quest_Framework_v7.6.jar" "C:\Users\Leone\DreamBot\Scripts\AI_Quest_Framework_v7.6.jar"
if %errorlevel% neq 0 (
    echo [ERROR] Failed to copy JAR to DreamBot Scripts directory!
    echo Please check if DreamBot directory exists: C:\Users\Leone\DreamBot\Scripts\
    call :maybe_pause
    exit /b 1
)
echo [SUCCESS] New version installed to DreamBot!

echo [7/7] Final verification...
if exist "C:\Users\Leone\DreamBot\Scripts\AI_Quest_Framework_v7.6.jar" (
    echo [SUCCESS] AI Quest Framework v7.6 is ready!
) else (
    echo [ERROR] Installation verification failed!
    call :maybe_pause
    exit /b 1
)

echo ========================================
echo [SUCCESS] AI QUEST FRAMEWORK v7.6 INSTALLED
echo ========================================
echo Script Location: C:\Users\Leone\DreamBot\Scripts\AI_Quest_Framework_v7.6.jar
echo Script Name: "AI Quest Framework v7.6"
echo Author: Leone
echo Version: 7.6 (Tree-Based Quest System + Romeo and Juliet + GE Integration)
echo Category: UTILITY
echo ========================================
echo Features in v7.6:
echo - Tree-based quest execution system with dynamic branching
echo - Romeo and Juliet quest implementation with complete dialogue handling
echo - GrandExchangeUtil integration for automated item purchasing
echo - Action nodes: TalkToNPC, WalkToLocation, InteractWithObject
echo - Decision nodes: QuestProgressDecision for config-based branching
echo - Progress-based quest resumption from any step
echo - Enhanced error handling and retry logic
echo - Scalable architecture for easy quest addition
echo Quest logs saved to: quest_logs\
echo ========================================

echo.
echo [IMPORTANT NOTES]:
echo 1. RESTART DreamBot completely to clear script cache
echo 2. Look for "AI Quest Framework v7.6" in your scripts
echo 3. If still showing old version, click "Refresh Scripts"
echo.
echo Ready to test! The tree-based system includes Romeo and Juliet quest.

echo [CLEANUP] Cleaning up build artifacts...
echo JAR file location: target\AI_Quest_Framework_v7.6.jar
echo.
if defined SKIP_PAUSE (
    set "cleanup_choice=%DEFAULT_CLEANUP_CHOICE%"
    echo Do you want to remove the target folder? ^(Y/N^) [AUTO=%cleanup_choice%]
) else (
    echo Do you want to remove the target folder? ^(Y/N^)
    set /p cleanup_choice=
)
if /i "%cleanup_choice%"=="Y" (
    echo Removing target folder...
    rmdir /s /q "target"
    echo [SUCCESS] Target folder removed
) else (
    echo Target folder preserved at: target\
)

call :maybe_pause
goto :end

:compile_error
echo [ERROR] Compilation failed!
echo Check the Java source files for syntax errors.
echo Make sure DreamBot client.jar is compatible with your code.
echo Check console output above for specific compilation errors.
call :maybe_pause
exit /b 1

:end

goto :eof

:maybe_pause
if not defined SKIP_PAUSE (
    pause
) else (
    REM Skipping pause in non-interactive mode
)
goto :eof
