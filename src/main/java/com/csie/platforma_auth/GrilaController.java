package com.csie.platforma_auth;

import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class GrilaController {

    private final AiService aiService;
    
    // Cheia va fi de forma "CSIE-2026-08-05", iar valoarea va fi JSON-ul curat al grilei
    private final Map<String, String> cacheGrile = new ConcurrentHashMap<>();

    public GrilaController(AiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping("/grila-zilei")
    public String grilaZilei(@RequestParam String facultate) throws Exception {
        // Transformăm numele facultății în litere mari pentru a evita chei duplicat (ex: "csie" vs "CSIE")
        String facultateKey = facultate.trim().toUpperCase();
        String cheieCache = facultateKey + "-" + LocalDate.now();

        // Dacă grila pentru această facultate a fost deja generată azi, o returnăm direct din cache
        if (cacheGrile.containsKey(cheieCache)) {
            return cacheGrile.get(cheieCache);
        }

        // AiService returnează deja JSON-ul curat primit de la Gemini datorită parserului org.json
        String raspunsAI = aiService.genereazaGrila(facultateKey);
        
        // Salvăm în memorie pentru următorii studenți care intră azi
        cacheGrile.put(cheieCache, raspunsAI);
        
        return raspunsAI;
    }
}
