package com.example.helloworld.weeklyserver.infra;

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

    public Optional<YoutubeVideo> searchFirst(String query) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("YouTube API KEY is missing");
            return Optional.empty();
        }
        try {
            String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url =
                    "https://www.googleapis.com/youtube/v3/search"
                            + "?part=snippet&type=video&maxResults=1"
                            + "&q=" + q
                            + "&key=" + apiKey;

            var res = restClient.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(SearchResponse.class);

            if (res == null || res.items == null || res.items.length == 0) return Optional.empty();

            var item = res.items[0];
            var videoId = item.id != null ? item.id.videoId : null;
            var title = item.snippet != null ? item.snippet.title : null;
            var thumb =
                    item.snippet != null && item.snippet.thumbnails != null
                            ? firstThumb(item.snippet.thumbnails)
                            : null;

            if (videoId == null) return Optional.empty();
            return Optional.of(new YoutubeVideo(videoId, title, thumb));
        } catch (Exception e) {
            log.warn("YouTube search failed: {}", e.getMessage());
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

    // ---- DTOs (간단 매핑용) ----
    record SearchResponse(Item[] items) {}
    static class Item { Id id; Snippet snippet; }
    static class Id { String kind; String videoId; }
    static class Snippet { String title; Thumbnails thumbnails; }
    static class Thumbnails {
        Thumb defaultThumb; Thumb medium; Thumb high; Thumb standard; Thumb maxres;
    }
    static class Thumb { String url; Integer width; Integer height; }
}
