/*
 * Rocket Prep.exe - starts the app with the Java runtime bundled next to it:
 *   runtime\bin\javaw.exe -cp app\* com.appsc.prep.desktop.MainKt
 * Built with MinGW by build_windows.sh.
 */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <wchar.h>

int WINAPI wWinMain(HINSTANCE inst, HINSTANCE prev, PWSTR args, int show) {
    wchar_t dir[MAX_PATH];
    DWORD n = GetModuleFileNameW(NULL, dir, MAX_PATH);
    if (n == 0 || n >= MAX_PATH) return 1;
    wchar_t *slash = wcsrchr(dir, L'\\');
    if (slash) *slash = 0;

    static wchar_t cmd[4 * MAX_PATH];
    _snwprintf(cmd, sizeof cmd / sizeof cmd[0] - 1,
        L"\"%ls\\runtime\\bin\\javaw.exe\" -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 "
        L"-Xss4m -cp \"%ls\\app\\*\" com.appsc.prep.desktop.MainKt",
        dir, dir);

    STARTUPINFOW si = { sizeof si };
    PROCESS_INFORMATION pi;
    if (!CreateProcessW(NULL, cmd, NULL, NULL, FALSE, 0, NULL, dir, &si, &pi)) {
        MessageBoxW(NULL,
            L"Rocket Prep could not start: the files next to Rocket Prep.exe are missing or damaged.\n"
            L"Please reinstall the app.",
            L"Rocket Prep", MB_ICONERROR);
        return 1;
    }
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    return 0;
}
