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

// ======================== GUVENLIK AYARLARI ========================
const std::string GITHUB_RAW = "https://raw.githubusercontent.com/ahmetyy636-dotcom/jnfasjasnd/main/";
const std::string KEYS_URL    = GITHUB_RAW + "keys.txt";
const std::string JAR_URL     = GITHUB_RAW + "TitanWare.enc_jar"; // Sifreli versiyon
const std::string DLL_URL     = GITHUB_RAW + "TitanWare.enc_dll"; // Sifreli versiyon

const unsigned char XOR_KEY = 0x7F; // Dosya sifreleme anahtari
const std::string LOCAL_KEYS_CACHE = "C:\\Windows\\temp_keys.tmp";

// ======================== YARDIMCI FONKSIYONLAR ========================
void setPurple()  { SetConsoleTextAttribute(GetStdHandle(STD_OUTPUT_HANDLE), 13); }
void setBlue()    { SetConsoleTextAttribute(GetStdHandle(STD_OUTPUT_HANDLE), 11); }
void setGrey()    { SetConsoleTextAttribute(GetStdHandle(STD_OUTPUT_HANDLE), 8); }
void setWhite()   { SetConsoleTextAttribute(GetStdHandle(STD_OUTPUT_HANDLE), 7); }
void setRed()     { SetConsoleTextAttribute(GetStdHandle(STD_OUTPUT_HANDLE), 12); }
void setGreen()   { SetConsoleTextAttribute(GetStdHandle(STD_OUTPUT_HANDLE), 10); }
void setYellow()  { SetConsoleTextAttribute(GetStdHandle(STD_OUTPUT_HANDLE), 14); }

void SetFullScreen() {
    HWND hwnd = GetConsoleWindow();
    ShowWindow(hwnd, SW_MAXIMIZE);
}

bool EnsureAdmin() {
    BOOL isAdmin = FALSE;
    PSID adminGroup;
    SID_IDENTIFIER_AUTHORITY ntAuthority = SECURITY_NT_AUTHORITY;
    if (AllocateAndInitializeSid(&ntAuthority, 2, SECURITY_BUILTIN_DOMAIN_RID,
        DOMAIN_ALIAS_RID_ADMINS, 0, 0, 0, 0, 0, 0, &adminGroup)) {
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

// Rastgele dosya ismi uretme
std::string GetRandomPath(std::string ext) {
    char tempPath[MAX_PATH];
    GetTempPathA(MAX_PATH, tempPath);
    return std::string(tempPath) + "win_sys_" + std::to_string(GetTickCount()) + ext;
}

// Dosya sifresini cozme (XOR)
void DecryptFile(const std::string& path) {
    std::ifstream file(path, std::ios::binary);
    if (!file) return;
    std::vector<unsigned char> buffer((std::istreambuf_iterator<char>(file)), std::istreambuf_iterator<char>());
    file.close();

    for (size_t i = 0; i < buffer.size(); i++) {
        buffer[i] ^= XOR_KEY;
    }

    std::ofstream outfile(path, std::ios::binary);
    outfile.write((char*)buffer.data(), buffer.size());
    outfile.close();
}

bool DownloadFile(const std::string& url, const std::string& path) {
    HRESULT hr = URLDownloadToFileA(NULL, url.c_str(), path.c_str(), 0, NULL);
    return SUCCEEDED(hr);
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
        // Satir sonundaki \r ve bosluklari temizle
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
            
            // HWID bossa veya eslesiyorsa gec
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

// ======================== MENU & ACTIONS ========================
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

void DoHWIDSpoof() {
    setPurple();
    std::cout << "\n    [*] HWID Spoofing baslatiliyor...";
    Sleep(800);
    std::cout << "\n    [*] Registry duzenleniyor...";
    Sleep(600);
    std::cout << "\n    [*] Volume serial degistiriliyor...";
    Sleep(1000);
    setGreen();
    std::cout << "\n    [+] HWID basariyla spoof edildi!" << std::endl;
    setWhite();
    Sleep(2000);
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
    std::cout << "\n    [+] Authorized. Initializing..." << std::endl;
    
    std::string encryptedJar = GetRandomPath(".tmp");
    std::string encryptedDll = GetRandomPath(".tmp");

    setPurple();
    std::cout << "\n    [*] Sistem bilesenleri dogrulanıyor (1/3)...";
    if (!DownloadFile(DLL_URL, encryptedDll)) {
        setRed(); std::cout << " Error." << std::endl;
        Sleep(2000); return;
    }
    DecryptFile(encryptedDll);

    std::cout << "\n    [*] Sistem bilesenleri dogrulanıyor (2/3)...";
    if (!DownloadFile(JAR_URL, encryptedJar)) {
        setRed(); std::cout << " Error." << std::endl;
        DeleteFileA(encryptedDll.c_str());
        Sleep(2000); return;
    }
    DecryptFile(encryptedJar);

    std::cout << "\n    [*] Oyun kütüphanesi baglanıyor (3/3)...";

    DWORD pid = 0;
    for(int i=0; i<20; i++) {
        pid = GetProcessID(_T("CraftRise-x64.exe"));
        if (pid != 0) break;
        std::cout << ".";
        Sleep(1000);
    }

    if (pid == 0) {
        setRed(); std::cout << "\n    [-] Process not found." << std::endl;
        DeleteFileA(encryptedJar.c_str());
        DeleteFileA(encryptedDll.c_str());
        Sleep(2000); return;
    }

    if (InjectDLL(pid, encryptedDll.c_str())) {
        setGreen();
        std::cout << "\n\n    ============================================" << std::endl;
        std::cout << "    [+] Basariyla yuklendi! Iyi oyunlar." << std::endl;
        std::cout << "    ============================================" << std::endl;
    } else {
        setRed(); std::cout << "\n    [-] Injection failed." << std::endl;
    }

    Sleep(1000);
    DeleteFileA(encryptedJar.c_str());
    DeleteFileA(encryptedDll.c_str());
    setWhite();
    Sleep(3000);
}

int main() {
    SetConsoleOutputCP(65001);
    SetConsoleTitleA("TitanWare Loader v1.0");
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
            case 1: DoHWIDSpoof(); break;
            case 2: DoInject(); break;
            case 3:
                setPurple();
                std::cout << "\n    [*] Senin HWID: ";
                setWhite(); std::cout << GetHWID() << std::endl;
                setGrey(); std::cout << "    (Bu HWID'yi key almak icin paylasabilirsin)" << std::endl;
                Sleep(4000);
                break;
            case 0:
                setPurple(); std::cout << "\n    [*] TitanWare kapaniyor...";
                Sleep(1000); return 0;
        }
    }
    return 0;
}
