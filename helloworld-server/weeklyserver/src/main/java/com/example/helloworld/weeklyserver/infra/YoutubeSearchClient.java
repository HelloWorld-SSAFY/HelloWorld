package com.example.helloworld.weeklyserver.infra;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

// import 생략
@Slf4j
@Component
@RequiredArgsConstructor
public class YoutubeSearchClient {

    private final RestClient rest = RestClient.builder().build();

    @Value("${youtube.api.key}") private String apiKey;

    private ObjectMapper mapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    }

    @Getter
    public static class YoutubeVideo {
        private final String videoId, title, url, thumbnailUrl;
        public YoutubeVideo(String videoId, String title, String thumbnailUrl) {
            this.videoId = videoId;
            this.title = title;
            this.url = "https://www.youtube.com/watch?v=" + videoId;
            this.thumbnailUrl = thumbnailUrl;
        }
    }

    public Optional<YoutubeVideo> searchFirst(String query) {
        return searchFirstWithMinDuration(query, 60); // 1분 = 60초
    }

    public Optional<YoutubeVideo> searchFirstWithMinDuration(String query, int minSeconds) {
        if (apiKey == null || apiKey.isBlank()) return Optional.empty();
        try {
            // 1) 검색 Top-N (원하는 만큼; 8~15 정도 권장)
            int max = 10;
            String sUrl = UriComponentsBuilder
                    .fromHttpUrl("https://www.googleapis.com/youtube/v3/search")
                    .queryParam("part", "snippet")
                    .queryParam("type", "video")
                    .queryParam("maxResults", 10)   // Top-N (1분 필터용)
                    .queryParam("order", "relevance") // 명시
                    //.queryParam("regionCode", "KR")  // 수동 테스트에 없었다면 주석
                    //.queryParam("relevanceLanguage", "ko") // 수동 테스트에 없었다면 주석
                    //.queryParam("safeSearch", "none") // 브라우저와 동일하게
                    .queryParam("q", query)          // 공백은 %20로 자동 인코딩
                    .queryParam("key", apiKey)
                    .encode(StandardCharsets.UTF_8)  // <- 공백 %20, 한글 %xx
                    .toUriString();
            log.info("[YouTube] URL={}", sUrl);

            String raw = rest.get().uri(sUrl).accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(String.class);
            SearchResponse sr = mapper().readValue(raw, SearchResponse.class);
            if (sr == null || sr.items == null || sr.items.length == 0) return Optional.empty();

            // 2) 상세 조회로 길이 확인
            String ids = java.util.Arrays.stream(sr.items)
                    .map(it -> it != null && it.id != null ? it.id.videoId : null)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.joining(","));
            if (ids.isEmpty()) return Optional.empty();

            String vUrl = "https://www.googleapis.com/youtube/v3/videos"
                    + "?part=snippet,contentDetails&id=" + ids + "&key=" + apiKey;

            String vRaw = rest.get().uri(vUrl).accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(String.class);
            VideosResponse vr = mapper().readValue(vRaw, VideosResponse.class);
            if (vr == null || vr.items == null || vr.items.length == 0) return Optional.empty();

            // 3) 검색 순서를 유지하며 'minSeconds 이상'인 첫 영상 선택
            java.util.Map<String, VideoItem> byId = new java.util.HashMap<>();
            for (VideoItem v : vr.items) byId.put(v.id, v);

            for (Item it : sr.items) {
                VideoItem v = byId.get(it.id.videoId);
                if (v == null || v.contentDetails == null) continue;
                long secs = parseDurationSec(v.contentDetails.duration); // ISO8601 → seconds
                if (secs >= minSeconds) {
                    String vid = v.id;
                    String title = v.snippet != null ? v.snippet.title : null;
                    String thumb = (v.snippet != null && v.snippet.thumbnails != null)
                            ? firstThumb(v.snippet.thumbnails) : null;
                    return Optional.of(new YoutubeVideo(vid, title, thumb));
                }
            }

            // 4) 없으면 원래 1순위로 fallback
            Item first = sr.items[0];
            String vid = first.id.videoId;
            String title = (first.snippet != null) ? first.snippet.title : null;
            String thumb = (first.snippet != null && first.snippet.thumbnails != null) ? firstThumb(first.snippet.thumbnails) : null;
            return Optional.of(new YoutubeVideo(vid, title, thumb));

        } catch (Exception e) {
            log.warn("[YouTube] search failed", e);
            return Optional.empty();
        }
    }

    private long parseDurationSec(String iso8601) {
        if (iso8601 == null) return 0L;
        return java.time.Duration.parse(iso8601).getSeconds();
    }


    // === util ===
    private String firstThumb(Thumbnails t) {
        if (t == null) return null;
        if (t.maxres != null) return t.maxres.url;
        if (t.high != null)   return t.high.url;
        if (t.medium != null) return t.medium.url;
        if (t.standard != null) return t.standard.url;
        if (t.defaultThumb != null) return t.defaultThumb.url;
        return null;
    }

    // === DTOs ===
    @JsonIgnoreProperties(ignoreUnknown = true) static class SearchResponse { Item[] items; }
    @JsonIgnoreProperties(ignoreUnknown = true) static class Item { Id id; Snippet snippet; }
    @JsonIgnoreProperties(ignoreUnknown = true) static class Id { String kind; String videoId; }
    @JsonIgnoreProperties(ignoreUnknown = true) static class Snippet { String title; Thumbnails thumbnails; }
    @JsonIgnoreProperties(ignoreUnknown = true) static class Thumbnails {
        @JsonProperty("default") Thumb defaultThumb; Thumb medium; Thumb high; Thumb standard; Thumb maxres;
    }
    @JsonIgnoreProperties(ignoreUnknown = true) static class Thumb { String url; Integer width; Integer height; }

    // videos.list 응답
    @JsonIgnoreProperties(ignoreUnknown = true) static class VideosResponse { VideoItem[] items; }
    @JsonIgnoreProperties(ignoreUnknown = true) static class VideoItem {
        String id; VSnippet snippet; VContent contentDetails;
    }
    @JsonIgnoreProperties(ignoreUnknown = true) static class VSnippet {
        String title; String description; List<String> tags;
        String categoryId; String defaultLanguage; String defaultAudioLanguage; Thumbnails thumbnails;
    }
    @JsonIgnoreProperties(ignoreUnknown = true) static class VContent { String duration; }
}

