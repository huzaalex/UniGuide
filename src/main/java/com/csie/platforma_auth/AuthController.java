package com.csie.platforma_auth;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate; //pt SQL

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController // Îi spune lui Spring Boot că această clasă ascultă butoanele de pe site (HTTP)
@RequestMapping("/api") // Toate rutele web de aici vor începe cu /api (ex: /api/register)
@CrossOrigin(origins = "*") // Permite paginii HTML să comunice cu serverul Java de pe calculatorul tău
public class AuthController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Map<String, Integer> incercariEsuate = new ConcurrentHashMap<>();
    private static final int MAX_INCERCARI = 5;

    @Value("${jwt.secret}")
            private String jwtSecretString;

            private SecretKey getSecretKey() {
                return Keys.hmacShaKeyFor(jwtSecretString.getBytes());
    }
    // Structură simplă folosită de Spring pentru a prelua automat JSON-ul trimis din butoanele HTML
    public static class UserRequest {
    public String username;
    public String parola;
    public String email;
}

    @PostMapping("/register") // Ruta web apelată când apeși butonul "Creează cont" în browser
        public ResponseEntity<String> inregistrare(@RequestBody UserRequest request) {
    String username = request.username;
    String parola1 = request.parola; // Păstrăm denumirea parola1
    String email = request.email;    // Preluăm noul câmp de email din request

    // 1. Verificăm în baza de date MySQL dacă numele de utilizator există deja
    if (utilizatorExista(username)) {
        return ResponseEntity.badRequest().body("❌ Eroare: Acest username este deja luat!");
    }

    // ==========================================
    // VALIDAREA NOUĂ PENTRU EMAIL
    // ==========================================
    if (email == null || !email.contains("@") || !email.contains(".")) {
        return ResponseEntity.badRequest().body("❌ Eroare: Introdu o adresă de email validă!");
    }

    // Verificăm dacă email-ul este deja folosit de altcineva în baza de date
    if (emailExista(email)) {
        return ResponseEntity.badRequest().body("❌ Eroare: Acest e-mail este deja asociat unui cont existent!");
    }

    // ==========================================
    // VALIDĂRILE TALE ACTUALE PENTRU PAROLĂ
    // ==========================================
    String eroareParola = valideazaParola(parola1);

        if (eroareParola != null) {
            return ResponseEntity.badRequest().body("❌ Eroare: " + eroareParola);
        }

    // ==========================================
    // SALVAREA ÎN MYSQL CU TOATE CELE 3 CÂMPURI
    // ==========================================
    try {
        String parolaHash = BCrypt.hashpw(parola1, BCrypt.gensalt());
        
        // Am adăugat coloana 'email' și al treilea semn de întrebare '?'
        String sqlInsert = "INSERT INTO utilizatori (username, parola_hash, email) VALUES (?, ?, ?)";
        
        // Executăm comanda SQL direct prin obiectul de Spring Boot transmitem 3 parametri
        jdbcTemplate.update(sqlInsert, username, parolaHash, email);
        
        return ResponseEntity.ok("✅ Înregistrare reușită! Datele au fost salvate permanent în baza de date MySQL.");
    } catch (Exception e) { 
        // Afișăm eroarea în consola serverului pentru a ști dacă tabelul nu e configurat bine
        e.printStackTrace(); 
        return ResponseEntity.internalServerError().body("❌ Eroare la salvarea în baza de date. Încearcă din nou.");
    }
}


    private String valideazaParola(String parola) {
    if (parola == null || parola.length() < 8) {
        return "Parola trebuie să aibă minim 8 caractere!";
    }

    if (!parola.matches(".*[A-Z].*")) {
        return "Parola trebuie să conțină cel puțin o literă mare!";
    }

    int numarCifre = parola.replaceAll("[^0-9]", "").length();

    if (numarCifre < 4) {
        return "Parola trebuie să conțină minim 4 cifre!";
    }

    if (!parola.matches(".*[^a-zA-Z0-9].*")) {
        return "Parola trebuie să conțină minim un caracter special!";
    }

    return null;
}

// ==========================================
// METODĂ AUXILIARĂ DE SIGURANȚĂ PENTRU EMAIL
// ==========================================
private boolean emailExista(String email) {
    try {
        String sql = "SELECT COUNT(*) FROM utilizatori WHERE email = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, email);
        return count != null && count > 0;
    } catch (Exception e) {
        return false;
    }
}



    @PostMapping("/login")
public ResponseEntity<Map<String, String>> login(@RequestBody UserRequest request) {
    String username = request.username;
    String parola = request.parola;
    Map<String, String> raspuns = new HashMap<>();

    // Verificăm dacă acest username a depășit limita de încercări (Logica ta neatinsă)
    int incercari = incercariEsuate.getOrDefault(username, 0);
    if (incercari >= MAX_INCERCARI) {
        raspuns.put("eroare", "Prea multe încercări eșuate. Încearcă din nou mai târziu.");
        return ResponseEntity.status(429).body(raspuns);
    }

    boolean parolaCorecta = false;

    // --- SCHIMBAREA PENTRU MYSQL ---
    try {
        // 1. Verificăm mai întâi dacă utilizatorul există în baza de date SQL
        if (utilizatorExista(username)) {
            // 2. Extragem direct hash-ul parolei stocate în MySQL pentru acest utilizator
            String sqlSelectHash = "SELECT parola_hash FROM utilizatori WHERE username = ?";
            String hashSalvat = jdbcTemplate.queryForObject(sqlSelectHash, String.class, username);

            // 3. Verificăm dacă parola introdusă acum corespunde cu hash-ul extras
            if (BCrypt.checkpw(parola, hashSalvat)) {
                parolaCorecta = true;
            }
        }
    } catch (Exception e) {
        // Dacă apare o eroare de conexiune la baza de date sau interogare
        raspuns.put("eroare", "Eroare internă la baza de date. Încearcă din nou.");
        return ResponseEntity.internalServerError().body(raspuns);
    }

    // --- LOGICA TA DE RĂSPUNS, TOKEN ȘI RATE LIMITING ---
    if (parolaCorecta) {
        incercariEsuate.remove(username); // Resetăm contorul la succes

        String token = Jwts.builder()
            .subject(username)
            .expiration(new Date(System.currentTimeMillis() + 86400000))
            .signWith(getSecretKey())
            .compact();
            
        raspuns.put("mesaj", "Autentificare reusita! Bine ai venit, " + username + ".");
        raspuns.put("token", token);
        return ResponseEntity.ok(raspuns);
    } else {
        incercariEsuate.merge(username, 1, Integer::sum); // Incrementăm eșecurile
        raspuns.put("eroare", "Username sau parolă incorectă.");
        return ResponseEntity.badRequest().body(Map.of("eroare", "Username sau parolă incorectă."));
    }
}


    private boolean utilizatorExista(String username) {
    try {
        // Îi cerem bazei de date să numere aparițiile acestui username
        String sqlCount = "SELECT COUNT(*) FROM utilizatori WHERE username = ?";
        Integer numar = jdbcTemplate.queryForObject(sqlCount, Integer.class, username);
        
        // Dacă a găsit cel puțin un rând, returnează true
        return numar != null && numar > 0;
    } catch (Exception e) {
        // În caz de eroare la baza de date, returnăm false ca măsură de siguranță
        return false;
    }
}


@Autowired
private org.springframework.mail.javamail.JavaMailSender mailSender;

public static class EmailRequest {
    public String email;
}

public static class ResetRequest {
    public String token;
    public String parolaNoua;
}

@PostMapping("/forgot-password")
public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody EmailRequest request) {
    Map<String, String> raspuns = new HashMap<>();

    // Mesaj identic indiferent dacă emailul există sau nu — previne enumerarea conturilor
    String mesajGeneric = "Dacă adresa există în sistem, vei primi un email cu instrucțiuni.";

    try {
        Integer numar = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM utilizatori WHERE email = ?", Integer.class, request.email);

        if (numar != null && numar > 0) {
            String token = java.util.UUID.randomUUID().toString();
            long expirare = System.currentTimeMillis() + (15 * 60 * 1000); // 15 minute

            jdbcTemplate.update(
                "UPDATE utilizatori SET reset_token = ?, reset_token_expiry = ? WHERE email = ?",
                token, expirare, request.email);

            trimiteEmailResetare(request.email, token);
        }
    } catch (Exception e) {
        // nu dezvăluim eroarea reală, doar logăm intern
        System.err.println("Eroare forgot-password: " + e.getMessage());
    }

    raspuns.put("mesaj", mesajGeneric);
    return ResponseEntity.ok(raspuns);
}

private void trimiteEmailResetare(String email, String token) {
    org.springframework.mail.SimpleMailMessage mesaj = new org.springframework.mail.SimpleMailMessage();
    mesaj.setTo(email);
    mesaj.setSubject("UniGuide — Resetare parolă");
    mesaj.setText("Ai cerut resetarea parolei.\n\n"
        + "Accesează acest link (valabil 15 minute):\n"
        + "http://localhost:8089/reset-password.html?token=" + token
        + "\n\nDacă nu ai cerut tu asta, ignoră acest email.");
    mailSender.send(mesaj);
}

@PostMapping("/reset-password")
public ResponseEntity<Map<String, String>> resetPassword(@RequestBody ResetRequest request) {
    Map<String, String> raspuns = new HashMap<>();

    try {
        var rezultate = jdbcTemplate.queryForList(
            "SELECT username, reset_token_expiry FROM utilizatori WHERE reset_token = ?", request.token);

        if (rezultate.isEmpty()) {
            raspuns.put("eroare", "Link invalid sau expirat.");
            return ResponseEntity.badRequest().body(raspuns);
        }

        long expirare = ((Number) rezultate.get(0).get("reset_token_expiry")).longValue();
        if (System.currentTimeMillis() > expirare) {
            raspuns.put("eroare", "Link invalid sau expirat.");
            return ResponseEntity.badRequest().body(raspuns);
        }

        String eroareParola = valideazaParola(request.parolaNoua);

            if (eroareParola != null) {
                raspuns.put("eroare", eroareParola);
                return ResponseEntity.badRequest().body(raspuns);
            }

        String parolaHash = BCrypt.hashpw(request.parolaNoua, BCrypt.gensalt());
        jdbcTemplate.update(
            "UPDATE utilizatori SET parola_hash = ?, reset_token = NULL, reset_token_expiry = NULL WHERE reset_token = ?",
            parolaHash, request.token);

        raspuns.put("mesaj", "Parola a fost schimbată cu succes!");
        return ResponseEntity.ok(raspuns);

    } catch (Exception e) {
        raspuns.put("eroare", "Eroare internă. Încearcă din nou.");
        return ResponseEntity.internalServerError().body(raspuns);
    }
}

/*Pentru Register cu Google acc */


public static class GoogleLoginRequest {
    public String idToken;
}

@PostMapping("/google-login")
public ResponseEntity<Map<String, String>> googleLogin(@RequestBody GoogleLoginRequest request) {
    Map<String, String> raspuns = new HashMap<>();

    try {
        // 1. Verificăm tokenul direct cu Google
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest verifyRequest = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create("https://oauth2.googleapis.com/tokeninfo?id_token=" + request.idToken))
            .GET().build();
        java.net.http.HttpResponse<String> verifyResponse = client.send(verifyRequest, java.net.http.HttpResponse.BodyHandlers.ofString());

        if (verifyResponse.statusCode() != 200) {
            raspuns.put("eroare", "Token Google invalid.");
            return ResponseEntity.status(401).body(raspuns);
        }

        org.json.JSONObject googleData = new org.json.JSONObject(verifyResponse.body());

        // 2. Verificăm că tokenul e emis pentru aplicația ta (nu alta)
        String clientIdTau = "922588552673-aegf48elcmkqr3a6nrl02hb3mh8a7757.apps.googleusercontent.com";
        if (!clientIdTau.equals(googleData.optString("aud"))) {
            raspuns.put("eroare", "Token Google invalid (audiență greșită).");
            return ResponseEntity.status(401).body(raspuns);
        }

        String email = googleData.getString("email");
        String googleId = googleData.getString("sub");
        boolean emailVerificat = googleData.optBoolean("email_verified", false);

        if (!emailVerificat) {
            raspuns.put("eroare", "Emailul Google nu este verificat.");
            return ResponseEntity.status(401).body(raspuns);
        }

        // 3. Căutăm dacă userul există deja (după email)
        var existent = jdbcTemplate.queryForList(
            "SELECT username FROM utilizatori WHERE email = ?", email);

        String username;
        if (!existent.isEmpty()) {
            username = (String) existent.get(0).get("username");
            // Actualizăm google_id, în caz că nu era setat
            jdbcTemplate.update("UPDATE utilizatori SET google_id = ? WHERE email = ?", googleId, email);
        } else {
            // Creăm cont nou — username derivat din partea de dinaintea @ din email
            username = email.split("@")[0] + "_" + googleId.substring(0, 5);
            jdbcTemplate.update(
                "INSERT INTO utilizatori (username, email, google_id, parola_hash) VALUES (?, ?, ?, NULL)",
                username, email, googleId);
        }

        // 4. Emitem propriul JWT, exact ca la login normal
        String token = Jwts.builder()
            .subject(username)
            .expiration(new Date(System.currentTimeMillis() + 86400000))
            .signWith(getSecretKey())
            .compact();

        raspuns.put("mesaj", "Autentificare reușită! Bine ai venit, " + username + ".");
        raspuns.put("token", token);
        raspuns.put("username", username);
        return ResponseEntity.ok(raspuns);

    } catch (Exception e) {
        raspuns.put("eroare", "Eroare la autentificarea cu Google.");
        return ResponseEntity.internalServerError().body(raspuns);
    }
}




}
