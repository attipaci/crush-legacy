@echo off

:: Find the absolute path to CRUSH 
set CRUSH=%~dp0

:: Set up the common variables for JAVA...
call "%CRUSH%wrapper.bat" imagetool

:: Run tool with the supplied arguments...
%JAVA% %JAVAOPTS% -classpath "%CLASSPATH%" crushtools.ImageTool %*

:: if run without arguments then wait for confirmation before exiting or
:: closing command window...
if "%1"=="" pause

