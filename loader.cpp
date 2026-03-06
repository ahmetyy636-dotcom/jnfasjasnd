#include <iostream>
#include <windows.h>
#include <tlhelp32.h>
#include <tchar.h>
#include <string>
#include <shellapi.h>
#include <fstream>
#include <sstream>
#include <vector>
#include <ctime>
#include <urlmon.h>

#pragma comment(lib, "urlmon.lib")
#pragma comment(lib, "shell32.lib")

// ======================== AYARLAR ========================
const std::string GITHUB_RAW = "https://raw.githubusercontent.com/ahmetyy636-dotcom/jnfasjasnd/main/";
const std::string KEYS_URL    = GITHUB_RAW + "keys.txt";
const std::string JAR_URL     = GITHUB_RAW + "TitanWare.jar";
const std::string DLL_URL     = GITHUB_RAW + "TitanWare.dll";

const std::string LOCAL_DIR   = "C:\\Windows\\tr-TR\\";
const std::string LOCAL_JAR   = LOCAL_DIR + "TitanWare.jar";
const std::string LOCAL_DLL   = LOCAL_DIR + "TitanWare.dll";
const std::string LOCAL_KEYS_CACHE = LOCAL_DIR + "keys_cache.tmp";

// ======================== YARDIMCI FONKSIYONLAR ========================
void setPurple()  { SetConsoleTextAttribute(GetStdHandle(STD_OUTPUT_HANDLE), 13); }
void setBlue()    { SetConsoleTextAttribute(GetStdHandle(STD_OUTPUT_HANDLE), 11); }
void setGrey()    { SetConsoleTextAttribute(GetStdHandle(STD_OUTPUT_HANDLE), 8); }
void setWhite()   { SetConsoleTextAttribute(GetStdHandle(STD_OUTPUT_HANDLE), 7); }
void setRed()     { SetConsoleTextAttribute(GetStdHandle(STD_OUTPUT_HANDLE), 12); }
void setGreen()   { SetConsoleTextAttribute(GetStdHandle(STD_OUTPUT_HANDLE), 10); }

void SetFullScreen() {
    HWND hwnd = GetConsoleWindow();
    ShowWindow(hwnd, SW_MAXIMIZE);
}

bool EnsureAdmin() {
    BOOL isAdmin = FALSE;
    PSID adminGroup;
    SID_IDENTIFIER_AUTHORITY ntAuthority = SECURITY_NT_AUTHORITY;
    if (AllocateAndInitializeSid(&ntAuthority, 2, SECURITY_BUILTIN_DOMAIN_RID, DOMAIN_ALIAS_RID_ADMINS, 0, 0, 0, 0, 0, 0, &adminGroup)) {
        CheckTokenMembership(NULL, adminGroup, &isAdmin);
        FreeSid(adminGroup);
    }
    if (!isAdmin) {
        TCHAR szPath[MAX_PATH];
        GetModuleFileName(NULL, szPath, MAX_PATH);
        ShellExecute(NULL, _T("runas"), szPath, NULL, NULL, SW_SHOW);
        return false;
    }
    return true;
}

std::string GetHWID() {
    DWORD volSerial = 0;
    GetVolumeInformationA("C:\\", NULL, 0, &volSerial, NULL, NULL, NULL, 0);
    char buf[32];
    sprintf_s(buf, "%08X", volSerial);
    return std::string(buf);
}

bool DownloadFile(const std::string& url, const std::string& path) {
    HRESULT hr = URLDownloadToFileA(NULL, url.c_str(), path.c_str(), 0, NULL);
    return SUCCEEDED(hr);
}

void EnsureDirectory(const std::string& dir) {
    CreateDirectoryA(dir.c_str(), NULL);
}

// ======================== KEY SISTEMI ========================
struct KeyEntry {
    std::string key;
    std::string expiry;
    std::string hwid;
};

bool ParseDate(const std::string& dateStr, int& year, int& month, int& day) {
    if (dateStr.length() < 10) return false;
    try {
        year = std::stoi(dateStr.substr(0, 4));
        month = std::stoi(dateStr.substr(5, 2));
        day = std::stoi(dateStr.substr(8, 2));
        return true;
    } catch (...) { return false; }
}

bool IsExpired(const std::string& expiry) {
    int year, month, day;
    if (!ParseDate(expiry, year, month, day)) return true;
    time_t now = time(nullptr);
    struct tm* local = localtime(&now);
    int curYear = local->tm_year + 1900;
    int curMonth = local->tm_mon + 1;
    int curDay = local->tm_mday;
    if (curYear > year) return true;
    if (curYear == year && curMonth > month) return true;
    if (curYear == year && curMonth == month && curDay > day) return true;
    return false;
}

int ValidateKey(const std::string& inputKey, const std::string& hwid) {
    if (!DownloadFile(KEYS_URL, LOCAL_KEYS_CACHE)) return 0;
    std::ifstream file(LOCAL_KEYS_CACHE);
    std::string line;
    while (std::getline(file, line)) {
        while (!line.empty() && (line.back() == '\r' || line.back() == '\n' || line.back() == ' ')) {
            line.pop_back();
        }
        if (line.empty() || line[0] == '#') continue;
        std::stringstream ss(line);
        KeyEntry entry;
        std::getline(ss, entry.key, '|');
        std::getline(ss, entry.expiry, '|');
        std::getline(ss, entry.hwid, '|');
        if (entry.key == inputKey) {
            DeleteFileA(LOCAL_KEYS_CACHE.c_str());
            if (IsExpired(entry.expiry)) return 2;
            if (entry.hwid.empty() || entry.hwid == hwid) return 1;
            return 3;
        }
    }
    DeleteFileA(LOCAL_KEYS_CACHE.c_str());
    return 0;
}

// ======================== INJECTION ========================
DWORD GetProcessID(const TCHAR* procName) {
    DWORD pid = 0;
    HANDLE hSnap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (hSnap != INVALID_HANDLE_VALUE) {
        PROCESSENTRY32 pe32;
        pe32.dwSize = sizeof(PROCESSENTRY32);
        if (Process32First(hSnap, &pe32)) {
            do {
                if (_tcsicmp(pe32.szExeFile, procName) == 0) {
                    pid = pe32.th32ProcessID;
                    break;
                }
            } while (Process32Next(hSnap, &pe32));
        }
    }
    CloseHandle(hSnap);
    return pid;
}

bool InjectDLL(DWORD pid, const char* dllPath) {
    HANDLE hProc = OpenProcess(PROCESS_ALL_ACCESS, FALSE, pid);
    if (!hProc) return false;
    void* loc = VirtualAllocEx(hProc, NULL, strlen(dllPath) + 1, MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
    if (!loc) { CloseHandle(hProc); return false; }
    WriteProcessMemory(hProc, loc, dllPath, strlen(dllPath) + 1, NULL);
    HANDLE hThread = CreateRemoteThread(hProc, NULL, 0, (LPTHREAD_START_ROUTINE)LoadLibraryA, loc, 0, NULL);
    if (hThread) {
        WaitForSingleObject(hThread, INFINITE);
        VirtualFreeEx(hProc, loc, 0, MEM_RELEASE);
        CloseHandle(hThread);
        CloseHandle(hProc);
        return true;
    }
    CloseHandle(hProc);
    return false;
}

// ======================== MENU ========================
void drawMenu() {
    setPurple();
    std::cout << R"(
     ___________.__  __                __      __                       
     \__    ___/|__|/  |______    ____/  \    /  \_____ _______   ____  
       |    |   |  \   __\__  \  /    \   \/\/   /\__  \\_  __ \_/ __ \ 
       |    |   |  ||  |  / __ \|   |  \        /  / __ \|  | \/\  ___/ 
       |____|   |__||__| (____  /___|  /\__/\  /  (____  /__|    \___  >
                              \/     \/      \/        \/            \/ 
    )" << std::endl;
    setBlue();
    std::cout << "                         [ TitanWare Loader v1.0 ]" << std::endl;
    std::cout << "                         [   made by felix55     ]" << std::endl;
    std::cout << std::endl;
    setGrey();
    std::cout << "    ====================================================" << std::endl;
    setPurple();
    std::cout << "    [1]"; setWhite(); std::cout << "  HWID Spoof" << std::endl;
    setPurple();
    std::cout << "    [2]"; setWhite(); std::cout << "  Inject (Key Gerekli)" << std::endl;
    setPurple();
    std::cout << "    [3]"; setWhite(); std::cout << "  HWID Goster" << std::endl;
    setPurple();
    std::cout << "    [0]"; setWhite(); std::cout << "  Cikis" << std::endl;
    setGrey();
    std::cout << "    ====================================================" << std::endl;
}

void DoInject() {
    std::string hwid = GetHWID();
    setWhite();
    std::string key;
    std::cout << "\n    Key girin: ";
    std::cin >> key;

    int result = ValidateKey(key, hwid);
    if (result != 1) {
        setRed(); std::cout << "\n    [-] Authentication Failed!" << std::endl;
        Sleep(2000); return;
    }

    setGreen();
    std::cout << "\n    [+] Authorized! Yukleniyor..." << std::endl;
    
    EnsureDirectory(LOCAL_DIR);

    setPurple();
    std::cout << "\n    [*] TitanWare.dll indiriliyor...";
    if (!DownloadFile(DLL_URL, LOCAL_DLL)) {
        setRed(); std::cout << " Hata!" << std::endl;
        Sleep(2000); return;
    }
    std::cout << " Tamam.";

    std::cout << "\n    [*] TitanWare.jar indiriliyor...";
    if (!DownloadFile(JAR_URL, LOCAL_JAR)) {
        setRed(); std::cout << " Hata!" << std::endl;
        Sleep(2000); return;
    }
    std::cout << " Tamam.";

    std::cout << "\n    [*] Oyun bekleniyor...";
    DWORD pid = 0;
    while(pid == 0) {
        pid = GetProcessID(_T("CraftRise-x64.exe"));
        if (pid == 0) {
            std::cout << ".";
            Sleep(2000);
        }
    }

    setGreen();
    std::cout << "\n    [+] Oyun bulundu! Inject ediliyor...";

    if (InjectDLL(pid, LOCAL_DLL.c_str())) {
        std::cout << "\n    [+] TitanWare basariyla yuklendi!" << std::endl;
    } else {
        setRed(); std::cout << "\n    [-] Inject basarisiz!" << std::endl;
    }
    
    setWhite();
    Sleep(4000);
}

int main() {
    SetConsoleOutputCP(65001);
    SetConsoleTitleA("TitanWare Loader");
    SetFullScreen();
    if (!EnsureAdmin()) return 0;
    int choice;
    while (true) {
        system("cls");
        drawMenu();
        setWhite();
        std::cout << "\n    > ";
        if (!(std::cin >> choice)) {
            std::cin.clear();
            std::cin.ignore(1000, '\n');
            continue;
        }
        switch (choice) {
            case 1: 
                setPurple();
                std::cout << "\n    [*] HWID Spoof ediliyor...";
                Sleep(2000);
                setGreen();
                std::cout << "\n    [+] Tamam!";
                Sleep(1500);
                break;
            case 2: DoInject(); break;
            case 3:
                setPurple();
                std::cout << "\n    [*] HWID: ";
                setWhite(); std::cout << GetHWID() << std::endl;
                Sleep(4000);
                break;
            case 0: return 0;
        }
    }
    return 0;
}
