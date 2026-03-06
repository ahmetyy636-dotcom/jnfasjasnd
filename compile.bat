@echo off
call "C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build\vcvarsall.bat" x64
cl /EHsc /MT /O2 loader.cpp /Fe:TitanWare.exe /link urlmon.lib shell32.lib user32.lib gdi32.lib advapi32.lib
if %ERRORLEVEL% EQU 0 (
    echo Compilation Successful!
) else (
    echo Compilation Failed!
)
