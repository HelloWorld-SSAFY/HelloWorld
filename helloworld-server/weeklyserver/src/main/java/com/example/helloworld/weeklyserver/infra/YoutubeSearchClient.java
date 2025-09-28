package com.example.helloworld.weeklyserver.infra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class YoutubeSearchClient {

    private final RestClient restClient = RestClient.builder().build();

    @Value("${youtube.api.key}")
    private String apiKey;

    @Getter
    public static class YoutubeVideo {
        private final String videoId;
        private final String title;
        private final String url;
        private final String thumbnailUrl;

        public YoutubeVideo(String videoId, String title, String thumbnailUrl) {
            this.videoId = videoId;
            this.title = title;
            this.url = "https://www.youtube.com/watch?v=" + videoId;
            this.thumbnailUrl = thumbnailUrl;
        }
    }

    // YoutubeSearchClient.java
    public Optional<YoutubeVideo> searchFirst(String query) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[YouTube] API KEY missing");
            return Optional.empty();
        }
        try {
            String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://www.googleapis.com/youtube/v3/search"
                    + "?part=snippet&type=video&maxResults=1&q=" + q + "&key=" + apiKey;

            // 1) 원문 JSON 로깅
            String raw = restClient.get().uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            log.info("[YouTube] raw={}", raw);   // ← 여기까지 보이면 HTTP/네트워크 OK

            // 2) 안전 파싱
            SearchResponse res = new com.fasterxml.jackson.databind.ObjectMapper()
                    .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(raw, SearchResponse.class);

            if (res == null || res.items == null || res.items.length == 0) {
                log.warn("[YouTube] No items for query: {}", query);
                return Optional.empty();
            }
            var item  = res.items[0];
            var vid   = (item.id != null) ? item.id.videoId : null;
            var title = (item.snippet != null) ? item.snippet.title : null;
            var thumb = (item.snippet != null && item.snippet.thumbnails != null) ? firstThumb(item.snippet.thumbnails) : null;

            if (vid == null) { log.warn("[YouTube] Missing videoId"); return Optional.empty(); }
            return Optional.of(new YoutubeVideo(vid, title, thumb));
        } catch (Exception e) {
            log.warn("[YouTube] search failed", e);
            return Optional.empty();
        }
    }


    private String firstThumb(Thumbnails t) {
        if (t.maxres != null) return t.maxres.url;
        if (t.high != null)   return t.high.url;
        if (t.medium != null) return t.medium.url;
        if (t.standard != null) return t.standard.url;
        if (t.defaultThumb != null) return t.defaultThumb.url;
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SearchResponse { Item[] items; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Item { Id id; Snippet snippet; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Id { String kind; String videoId; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Snippet {
        String title;
        Thumbnails thumbnails;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Thumbnails {
        @JsonProperty("default") Thumb defaultThumb;  // ← 예약어 매핑
        Thumb medium; Thumb high; Thumb standard; Thumb maxres;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Thumb { String url; Integer width; Integer height; }
}
