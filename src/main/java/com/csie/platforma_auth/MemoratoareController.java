package com.csie.platforma_auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MemoratoareController {

private final AiService aiService;

// Dimensiune maximă acceptată, în bytes (5 MB) — mai strict decât limita globală din properties
private static final long MARIME_MAXIMA = 5 * 1024 * 1024;

// Tipuri de continut acceptate ("Memoratoare": flashcards, chestionar, cheatsheet)
private static final java.util.Set<String> TIPURI_VALIDE = java.util.Set.of("flashcards", "chestionar", "cheatsheet");

public MemoratoareController(AiService aiService) {
    this.aiService = aiService;
}

@PostMapping("/genereaza-din-curs")
public ResponseEntity<Map<String, Object>> genereazaDinCurs(
        @RequestParam("fisiere") MultipartFile[] fisiere,
        @RequestParam("tip") String tip) {

    Map<String, Object> raspuns = new HashMap<>();

    // 1. Verificare tip
    if (!TIPURI_VALIDE.contains(tip)) {
        raspuns.put("eroare", "Tip de conținut invalid.");
        return ResponseEntity.badRequest().body(raspuns);
    }

    // 2. Verificare dacă există fișiere
    if (fisiere == null || fisiere.length == 0) {
        raspuns.put("eroare", "Niciun fișier încărcat.");
        return ResponseEntity.badRequest().body(raspuns);
    }

    // Listele pe care le vom trimite către AI
    java.util.List<byte[]> continuturi = new java.util.ArrayList<>();
    java.util.List<String> mimeTypes = new java.util.ArrayList<>();

    // 3. Verificăm fiecare fișier
    for (MultipartFile fisier : fisiere) {

        if (fisier == null || fisier.isEmpty()) {
            raspuns.put("eroare", "Unul dintre fișiere este gol.");
            return ResponseEntity.badRequest().body(raspuns);
        }

        if (fisier.getSize() > MARIME_MAXIMA) {
            raspuns.put(
                "eroare",
                "Fișierul \"" + fisier.getOriginalFilename() +
                "\" este prea mare (maxim 5 MB)."
            );
            return ResponseEntity.badRequest().body(raspuns);
        }

        try {
            byte[] continutFisier = fisier.getBytes();

            String mimeTypeReal = detecteazaTipReal(continutFisier);

            if (mimeTypeReal == null) {
                raspuns.put(
                    "eroare",
                    "Fișierul \"" + fisier.getOriginalFilename() +
                    "\" nu este un JPG, PNG sau PDF valid."
                );
                return ResponseEntity.badRequest().body(raspuns);
            }

            continuturi.add(continutFisier);
            mimeTypes.add(mimeTypeReal);

        } catch (Exception e) {
            raspuns.put(
                "eroare",
                "Nu am putut citi fișierul \"" +
                fisier.getOriginalFilename() + "\"."
            );
            return ResponseEntity.badRequest().body(raspuns);
        }
    }

    // 4. Trimitem TOATE fișierele către AI
    try {

        String rezultatJson =
            aiService.genereazaDinFisiere(
                continuturi,
                mimeTypes,
                tip
            );

        raspuns.put("rezultat", rezultatJson);

        return ResponseEntity.ok(raspuns);

    } catch (Exception e) {

        e.printStackTrace();

        raspuns.put(
            "eroare",
            "Eroare la procesarea fișierelor. Încearcă din nou."
        );

        return ResponseEntity
                .internalServerError()
                .body(raspuns);
    }
}

/**
 * Verifică semnătura reală (magic bytes) a fișierului.
 * Întoarce MIME type-ul corect dacă fișierul e valid, sau null dacă nu recunoaștem formatul.
 */
private String detecteazaTipReal(byte[] bytes){
    if (bytes.length < 8) return null;

    // JPEG: FF D8 FF
    if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
        return "image/jpeg";
    }

    // PNG: 89 50 4E 47 0D 0A 1A 0A
    if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
            && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
        return "image/png";
    }

    // PDF: 25 50 44 46 ("%PDF")
    if (bytes[0] == 0x25 && bytes[1] == 0x50 && bytes[2] == 0x44 && bytes[3] == 0x46) {
        return "application/pdf";
    }

    return null; // orice altceva e respins
    }
}