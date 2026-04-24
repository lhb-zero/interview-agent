@echo off
set OLLAMA_FLASH_ATTENTION=1
set OLLAMA_CONTEXT_LENGTH=4096
start "" "C:\Users\Zero\AppData\Local\Programs\Ollama\ollama.exe" serve
