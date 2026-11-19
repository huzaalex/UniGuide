package com.csie.platforma_auth;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Random;

@Service
public class AiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final HttpClient client = HttpClient.newHttpClient();
    private final Random random = new Random();

    private static final List<String> SUBIECTE_MATEMATICA = List.of(
        "limite de funcții", "derivate și aplicații (monotonie, extreme)",
        "integrale definite", "integrale nedefinite (primitive)",
        "șiruri de numere (recurență, convergență)", "progresii aritmetice și geometrice",
        "combinatorică (permutări, aranjamente, combinări)", "probabilități",
        "numere complexe", "polinoame și ecuații algebrice",
        "matrice și determinanți", "sisteme de ecuații liniare",
        "trigonometrie (ecuații și identități)", "geometrie analitică (drepte, conice)",
        "funcții (studiul complet, asimptote)"
    );

    private static final List<String> SUBIECTE_INFORMATICA = List.of(
        "recursivitate", "șiruri de caractere (prelucrare, algoritmi pe stringuri)",
        "vectori și matrice (algoritmi de bază)", "algoritmi de sortare",
        "complexitate algoritmică (notația Big-O)", "structuri de date (stive, cozi, liste)",
        "combinatorică aplicată (generare submulțimi, permutări)",
        "programare dinamică (probleme simple)", "grafuri (parcurgere BFS/DFS)",
        "numere prime și divizibilitate",
        "complexitatiile algoritmilor"
    );

    private static final List<String> SUBIECTE_FIZICA = List.of(
        "cinematică (mișcare rectilinie, viteză, accelerație)",
        "dinamică (legile lui Newton, forțe)",
        "lucru mecanic și energie", "termodinamică (legile gazului ideal)",
        "electricitate (legea lui Ohm, circuite)", "unde și oscilații",
        "optică geometrică"
    );

    private String alegeSubiect(String facultate) {
        // ASE e orientat economic → mai mult matematică/informatică, fără fizică
        boolean areFizica = !facultate.toUpperCase().contains("ASE");

        int tip = areFizica ? random.nextInt(3) : random.nextInt(2);
        List<String> lista = switch (tip) {
            case 0 -> SUBIECTE_MATEMATICA;
            case 1 -> SUBIECTE_INFORMATICA;
            default -> SUBIECTE_FIZICA;
        };
        String materie = switch (tip) {
            case 0 -> "matematica";
            case 1 -> "informatica";
            default -> "fizica";
        };
        String subiect = lista.get(random.nextInt(lista.size()));
        return materie + ": " + subiect;
    }

public String genereazaDinFisiere(
        List<byte[]> continuturi,
        List<String> mimeTypes,
        String tip) throws Exception {

    String promptInstructiuni = switch (tip) {

        case "flashcards" -> """
            Analizeaza TOATE imaginile/documentele atasate.
            Acestea reprezinta pagini ale aceluiasi material de curs.

            Genereaza 6-10 flashcards bazate pe INTREGUL material,
            nu separat pentru fiecare imagine.

            Acopera conceptele importante si evita repetitiile.

            Fiecare flashcard trebuie sa contina:
            - intrebare scurta
            - raspuns scurt si clar

            Raspunde STRICT in acest format JSON:
            {"tip":"flashcards","titlu":"...","elemente":[{"intrebare":"...","raspuns":"..."}]}
            """;

        case "chestionar" -> """
            Analizeaza TOATE imaginile/documentele atasate.
            Acestea reprezinta pagini ale aceluiasi material de curs.

            Genereaza 5 intrebari grila bazate pe INTREGUL material.

            Fiecare intrebare trebuie sa aiba 4 variante.
            Evita intrebarile repetitive.

            Raspunde STRICT in acest format JSON:
            {"tip":"chestionar","titlu":"...","elemente":[{"intrebare":"...","variante":["...","...","...","..."],"raspunsCorect":0}]}
            """;

        default -> """
            Analizeaza TOATE imaginile/documentele atasate.
            Acestea reprezinta pagini ale aceluiasi material de curs.

            Genereaza un cheat sheet concis si bine organizat
            care sa acopere conceptele esentiale din INTREGUL material.

            Daca exista formule matematice,
            incadreaza-le intre $ ... $ (LaTeX).

            Raspunde STRICT in acest format JSON:
            {"tip":"cheatsheet","titlu":"...","elemente":[{"sectiune":"...","continut":"..."}]}
            """;
    };

    // Construim parts pentru Gemini
    org.json.JSONArray parts = new org.json.JSONArray();

    // Mai intai punem instructiunile
    parts.put(
        new JSONObject()
            .put("text", promptInstructiuni)
    );

    // Apoi adaugam TOATE fisierele
    for (int i = 0; i < continuturi.size(); i++) {

        parts.put(
            new JSONObject()
                .put("inlineData",
                    new JSONObject()
                        .put("mimeType", mimeTypes.get(i))
                        .put(
                            "data",
                            java.util.Base64
                                .getEncoder()
                                .encodeToString(continuturi.get(i))
                        )
                )
        );
    }

    String jsonBody = new JSONObject()
        .put(
            "contents",
            new org.json.JSONArray()
                .put(
                    new JSONObject()
                        .put("parts", parts)
                )
        )
        .put(
            "generationConfig",
            new JSONObject()
                .put("responseMimeType", "application/json")
        )
        .toString();

    String url =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key="
        + apiKey;

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
        .build();

HttpResponse<String> response =
        client.send(request, HttpResponse.BodyHandlers.ofString());

JSONObject root = new JSONObject(response.body());

String text = root
        .getJSONArray("candidates")
        .getJSONObject(0)
        .getJSONObject("content")
        .getJSONArray("parts")
        .getJSONObject(0)
        .getString("text");

// Repară backslash-urile din LaTeX
text = reparaJsonLatex(text);

// Verifică și serializează din nou JSON-ul
JSONObject rezultat = new JSONObject(text);

return rezultat.toString();
}
private String reparaJsonLatex(String json) {
    return json.replaceAll(
        "\\\\(?![\"\\\\/bfnrt]|u[0-9a-fA-F]{4})",
        "\\\\\\\\"
    );
}

    public String genereazaGrila(String facultate) throws Exception {
        String subiectAles = alegeSubiect(facultate);

        String prompt = """
            Genereaza o singura intrebare grila de admitere, specifica pentru %s,
            la nivel de liceu, STRICT pe subiectul: %s.
            Nu genera intrebari despre matrice, cu exceptia cazului in care subiectul ales este chiar despre matrice.
            Varia stilul si structura problemei fata de un exercitiu tipic/clasic.
            Daca intrebarea sau vreo varianta de raspuns contine formule matematice,
            scrie-le in format LaTeX, incadrate STRICT intre delimitatori $ ... $
            (atat in campul "intrebare" cat si in fiecare element din "variante" si in "explicatie").
            Daca o varianta nu contine formule (e text simplu), nu adauga $ deloc.
            Raspunde STRICT in acest format JSON, fara text suplimentar:
            {
              "intrebare": "...",
              "variante": ["...", "...", "...", "..."],
              "raspunsCorect": 0,
              "explicatie": "..."
            }
            """.formatted(facultate, subiectAles);

        String jsonBody = """
            {
              "contents": [{
                "parts": [{"text": %s}]
              }],
              "generationConfig": {
                "responseMimeType": "application/json"
              }
            }
            """.formatted(JSONObject.quote(prompt));

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=" + apiKey;
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        JSONObject root = new JSONObject(response.body());
        return root.getJSONArray("candidates")
                   .getJSONObject(0)
                   .getJSONObject("content")
                   .getJSONArray("parts")
                   .getJSONObject(0)
                   .getString("text");
    }
}