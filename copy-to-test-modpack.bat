@echo off
set "MODS=C:\Users\Ksivi\curseforge\minecraft\Instances\dead air tests\mods"
echo Copying both mods to: %MODS%
copy /Y "build\libs\dead_air-1.2.jar" "%MODS%\"
copy /Y "%USERPROFILE%\MCreatorWorkspaces\radioos\build\libs\radiotowers-1.0.5.jar" "%MODS%\"
echo Done. Build both projects in IDE first if you want new timestamps in the modpack.
pause
