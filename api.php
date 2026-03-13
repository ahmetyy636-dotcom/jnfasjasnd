<?php
header('Content-Type: application/json; charset=utf-8');

/**
 * CraftRise Oyuncu Durumu Sorgulama API
 * Kullanım: api.php?username=OYUNCU_ADI
 */

if (!isset($_GET['username']) || empty($_GET['username'])) {
    echo json_encode([
        "status" => "error",
        "message" => "Lutfen bir kullanici adi belirtin. Ornek: api.php?username=oxin"
    ]);
    exit;
}

$username = htmlspecialchars($_GET['username']);
$url = 'https://www.craftrise.com.tr/posts/post-search.php';

// Ziyaretçinin IP adresini al
$user_ip = $_SERVER['REMOTE_ADDR'] ?? '127.0.0.1';
if (!empty($_SERVER['HTTP_CLIENT_IP'])) {
    $user_ip = $_SERVER['HTTP_CLIENT_IP'];
} elseif (!empty($_SERVER['HTTP_X_FORWARDED_FOR'])) {
    $user_ip = $_SERVER['HTTP_X_FORWARDED_FOR'];
}

// Cihaz ve tarayıcı taklidi (Headers)
$headers = [
    'authority: www.craftrise.com.tr',
    'accept: application/json, text/javascript, */*; q=0.01',
    'origin: https://www.craftrise.com.tr',
    'referer: https://www.craftrise.com.tr/',
    'user-agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'x-requested-with: XMLHttpRequest',
    'content-type: application/x-www-form-urlencoded; charset=UTF-8',
    'X-Forwarded-For: ' . $user_ip,
    'Client-IP: ' . $user_ip,
    'X-Real-IP: ' . $user_ip
];

$postData = http_build_query(['username' => $username]);

// cURL ile istek gönderimi
$ch = curl_init($url);
curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
curl_setopt($ch, CURLOPT_POST, true);
curl_setopt($ch, CURLOPT_POSTFIELDS, $postData);
curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);
curl_setopt($ch, CURLOPT_TIMEOUT, 10);
curl_setopt($ch, CURLOPT_FOLLOWLOCATION, true);

// Bazı sunucularda SSL hatası olmaması için
curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);

$response = curl_exec($ch);
$httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
curl_close($ch);

if ($httpCode == 200) {
    $result = json_decode($response, true);
    $msg = isset($result['resultMessage']) ? mb_strtolower($result['resultMessage'], 'UTF-8') : '';

    $isBanned = false;
    $statusText = "Aktif (Banli Degil)";

    // Ban kontrolü
    if (isset($result['resultType']) && $result['resultType'] == 'error' && strpos($msg, 'engellenmiş') !== false) {
        $isBanned = true;
        $statusText = "Yasaklanmis (Banli)";
    }

    echo json_encode([
        "status" => "success",
        "data" => [
            "username" => $username,
            "is_banned" => $isBanned,
            "status_message" => $statusText,
            "avatar" => "https://www.craftrise.com.tr/gets/get-head.php?s=256&u=" . $username
        ]
    ], JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);

} elseif ($httpCode == 403) {
    echo json_encode([
        "status" => "error",
        "message" => "Erisim Reddedildi (403). Sunucu botu engelledi."
    ]);
} else {
    echo json_encode([
        "status" => "error",
        "message" => "Sunucu hatasi oluştu. Kod: " . $httpCode
    ]);
}
?>