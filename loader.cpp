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
// GitHub raw URL'leri — repo: ahmetyy636-dotcom/jnfasjasnd
const std::string GITHUB_RAW = "https://raw.githubusercontent.com/ahmetyy636-dotcom/jnfasjasnd/main/";
const std::string KEYS_URL    = GITHUB_RAW + "keys.txt";
const std::string JAR_URL     = GITHUB_RAW + "TitanWare.jar";
const std::string DLL_URL     = GITHUB_RAW + "TitanWare.dll";

// Yerel dosya yollari
const std::string LOCAL_DIR   = "C:\\Windows\\tr-TR\\";
const std::string LOCAL_JAR   = LOCAL_DIR + "TitanWare.jar";
const std::string LOCAL_DLL   = LOCAL_DIR + "TitanWare.dll";
const std::string LOCAL_KEYS  = LOCAL_DIR + "keys_cache.txt";
const std::string PROCESS_NAME = "CraftRise-x64.exe";
// =========================================================

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

// ===================== ADMIN YETKI =====================
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

// ===================== HWID =====================
std::string GetHWID() {
    DWORD volSerial = 0;
    GetVolumeInformationA("C:\\", NULL, 0, &volSerial, NULL, NULL, NULL, 0);
    char buf[32];
    sprintf_s(buf, "%08X", volSerial);
    return std::string(buf);
}

// ===================== DOSYA INDIRME =====================
bool DownloadFile(const std::string& url, const std::string& path) {
    HRESULT hr = URLDownloadToFileA(NULL, url.c_str(), path.c_str(), 0, NULL);
    return SUCCEEDED(hr);
}

// ===================== KLASOR OLUSTURMA =====================
void EnsureDirectory(const std::string& dir) {
    CreateDirectoryA(dir.c_str(), NULL);
}

// ===================== KEY SISTEMI =====================
// keys.txt formati: KEY|BITIS_TARIHI(YYYY-MM-DD)|HWID (HWID bos ise herkes kullanabilir)
// Ornek: TITAN-ABCD-1234|2026-12-31|
// Ornek: TITAN-EFGH-5678|2026-06-15|A1B2C3D4

struct KeyEntry {
    std::string key;
    std::string expiry; // YYYY-MM-DD
    std::string hwid;   // bos = herkes kullanabilir
};

bool ParseDate(const std::string& dateStr, int& year, int& month, int& day) {
    if (dateStr.length() < 10) return false;
    try {
        year = std::stoi(dateStr.substr(0, 4));
        month = std::stoi(dateStr.substr(5, 2));
        day = std::stoi(dateStr.substr(8, 2));
        return true;
    } catch (...) {
        return false;
    }
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

std::vector<KeyEntry> LoadKeys(const std::string& filePath) {
    std::vector<KeyEntry> keys;
    std::ifstream file(filePath);
    std::string line;
    while (std::getline(file, line)) {
        if (line.empty() || line[0] == '#') continue; // yorum satiri
        std::stringstream ss(line);
        KeyEntry entry;
        std::getline(ss, entry.key, '|');
        std::getline(ss, entry.expiry, '|');
        std::getline(ss, entry.hwid, '|');
        keys.push_back(entry);
    }
    return keys;
}

// 0 = gecersiz, 1 = gecerli, 2 = suresi dolmus, 3 = hwid uyumsuz
int ValidateKey(const std::string& inputKey, const std::string& hwid) {
    // GitHub'dan keyleri indir
    setPurple();
    std::cout << "\n    [*] Keyler kontrol ediliyor...";
    
    if (!DownloadFile(KEYS_URL, LOCAL_KEYS)) {
        setRed();
        std::cout << "\n    [-] Key sunucusuna baglanilamadi!";
        return 0;
    }

    std::vector<KeyEntry> keys = LoadKeys(LOCAL_KEYS);
    DeleteFileA(LOCAL_KEYS.c_str()); // cache temizle

    for (const auto& k : keys) {
        if (k.key == inputKey) {
            // Key bulundu
            if (IsExpired(k.expiry)) {
                return 2; // suresi dolmus
            }
            if (!k.hwid.empty() && k.hwid != hwid) {
                return 3; // hwid uyumsuz
            }
            return 1; // gecerli
        }
    }
    return 0; // bulunamadi
}

// ===================== PROCESS BULMA =====================
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

// ===================== DLL INJECT =====================
bool InjectDLL(DWORD pid, const char* dllPath) {
    HANDLE hProc = OpenProcess(PROCESS_ALL_ACCESS, FALSE, pid);
    if (!hProc) return false;

    void* loc = VirtualAllocEx(hProc, NULL, strlen(dllPath) + 1, MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
    if (!loc) { CloseHandle(hProc); return false; }

    WriteProcessMemory(hProc, loc, dllPath, strlen(dllPath) + 1, NULL);
    HANDLE hThread = CreateRemoteThread(hProc, NULL, 0,
        (LPTHREAD_START_ROUTINE)LoadLibraryA, loc, 0, NULL);

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

// ===================== MENU =====================
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
    std::cout << "                         [   made by oxinxcom    ]" << std::endl;
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

// ===================== HWID SPOOF =====================
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

// ===================== INJECT =====================
void DoInject() {
    std::string hwid = GetHWID();

    // Key girisi
    setWhite();
    std::string key;
    std::cout << "\n    Key girin: ";
    std::cin >> key;

    int result = ValidateKey(key, hwid);

    switch (result) {
        case 0:
            setRed();
            std::cout << "\n    [-] Gecersiz key!" << std::endl;
            Sleep(2000);
            return;
        case 2:
            setYellow();
            std::cout << "\n    [-] Key suresi dolmus! Yeni key satin alin." << std::endl;
            Sleep(2000);
            return;
        case 3:
            setRed();
            std::cout << "\n    [-] Bu key baska bir cihaza bagli!" << std::endl;
            Sleep(2000);
            return;
    }

    setGreen();
    std::cout << "\n    [+] Key gecerli! Yukleme basliyor..." << std::endl;
    Sleep(500);

    // Klasoru olustur
    EnsureDirectory(LOCAL_DIR);

    // GitHub'dan dosyalari indir
    setPurple();
    std::cout << "\n    [*] TitanWare.dll indiriliyor...";
    if (!DownloadFile(DLL_URL, LOCAL_DLL)) {
        setRed();
        std::cout << "\n    [-] DLL indirilemedi!" << std::endl;
        Sleep(2000);
        return;
    }
    setGreen();
    std::cout << " OK" << std::endl;

    setPurple();
    std::cout << "    [*] TitanWare.jar indiriliyor...";
    if (!DownloadFile(JAR_URL, LOCAL_JAR)) {
        setRed();
        std::cout << "\n    [-] JAR indirilemedi!" << std::endl;
        Sleep(2000);
        return;
    }
    setGreen();
    std::cout << " OK" << std::endl;

    // Oyun procesini bul
    setPurple();
    std::cout << "\n    [*] " << PROCESS_NAME << " araniyor...";

    DWORD pid = 0;
    int attempts = 0;
    while (pid == 0 && attempts < 30) {
        pid = GetProcessID(_T("CraftRise-x64.exe"));
        if (pid == 0) {
            if (attempts == 0) {
                setYellow();
                std::cout << "\n    [!] Oyun bulunamadi. Oyunu acin, bekleniyor";
            }
            std::cout << ".";
            Sleep(2000);
            attempts++;
        }
    }

    if (pid == 0) {
        setRed();
        std::cout << "\n    [-] Oyun bulunamadi! Oyunun acik oldugundan emin olun." << std::endl;
        Sleep(3000);
        return;
    }

    setGreen();
    std::cout << "\n    [+] Oyun bulundu! PID: " << pid << std::endl;

    // DLL Inject
    setPurple();
    std::cout << "    [*] TitanWare inject ediliyor...";
    Sleep(500);

    if (InjectDLL(pid, LOCAL_DLL.c_str())) {
        setGreen();
        std::cout << "\n\n    ============================================" << std::endl;
        std::cout << "    [+] TitanWare basariyla inject edildi!" << std::endl;
        std::cout << "    [+] Iyi oyunlar! :)" << std::endl;
        std::cout << "    ============================================" << std::endl;
    } else {
        setRed();
        std::cout << "\n    [-] Inject basarisiz! Antivirusi kapatin." << std::endl;
    }

    setWhite();
    Sleep(4000);
}

// ===================== MAIN =====================
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
            case 1:
                DoHWIDSpoof();
                break;
            case 2:
                DoInject();
                break;
            case 3: {
                setPurple();
                std::cout << "\n    [*] Senin HWID: ";
                setWhite();
                std::cout << GetHWID() << std::endl;
                setGrey();
                std::cout << "    (Bu HWID'yi key almak icin paylasabilirsin)" << std::endl;
                Sleep(4000);
                break;
            }
            case 0:
                setPurple();
                std::cout << "\n    [*] TitanWare kapaniyor...";
                Sleep(1000);
                return 0;
        }
    }
    return 0;
}
