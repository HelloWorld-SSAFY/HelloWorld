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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[YouTube] API key is missing");
            return Optional.empty();
        }

        try {
            // API 키 일부 로그 (디버깅용)
            log.info("[YouTube] Using API Key: {}...", apiKey.substring(0, Math.min(10, apiKey.length())));

            // 1) 검색 Top-N (원하는 만큼; 8~15 정도 권장)
            int max = 10;
            String sUrl = UriComponentsBuilder
                    .fromHttpUrl("https://www.googleapis.com/youtube/v3/search")
                    .queryParam("part", "snippet")
                    .queryParam("type", "video")
                    .queryParam("maxResults", 10)   // Top-N (1분 필터용)
                    .queryParam("order", "relevance") // 명시
                    .queryParam("regionCode", "KR")  // 한국 지역 코드 활성화
                    .queryParam("relevanceLanguage", "ko") // 한국어 활성화
                    .queryParam("safeSearch", "moderate") // 안전 검색
                    .queryParam("q", query)          // 공백은 %20로 자동 인코딩
                    .queryParam("key", apiKey)
                    .encode(StandardCharsets.UTF_8)  // <- 공백 %20, 한글 %xx
                    .toUriString();
            log.info("[YouTube] Search URL: {}", sUrl);

            // 실제 응답 원문 확인
            String raw = rest.get().uri(sUrl).accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(String.class);

            log.info("[YouTube] Raw response length: {}", raw != null ? raw.length() : 0);
            log.info("[YouTube] Raw response preview (first 500 chars): {}",
                    raw != null ? raw.substring(0, Math.min(500, raw.length())) + "..." : "null");

            SearchResponse sr = mapper().readValue(raw, SearchResponse.class);
            if (sr == null || sr.items == null || sr.items.length == 0) {
                log.warn("[YouTube] No search results found after parsing");
                return Optional.empty();
            }

            log.info("[YouTube] Parsed {} search results", sr.items.length);

            // 첫 번째 결과 상세 로깅
            if (sr.items.length > 0) {
                Item first = sr.items[0];
                log.info("[YouTube] First result - videoId: {}, title: {}",
                        first.id != null ? first.id.videoId : "null",
                        first.snippet != null ? first.snippet.title : "null");
            }

            // 2) 상세 조회로 길이 확인
            String ids = Arrays.stream(sr.items)
                    .filter(Objects::nonNull)
                    .filter(item -> item.id != null && item.id.videoId != null)
                    .map(item -> item.id.videoId)
                    .collect(Collectors.joining(","));

            if (ids.isEmpty()) {
                log.warn("[YouTube] No valid video IDs found");
                return Optional.empty();
            }

            log.info("[YouTube] Video IDs for details: {}", ids);

            String vUrl = "https://www.googleapis.com/youtube/v3/videos"
                    + "?part=snippet,contentDetails&id=" + ids + "&key=" + apiKey;

            log.info("[YouTube] Videos detail URL: {}", vUrl);

            String vRaw = rest.get().uri(vUrl).accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(String.class);

            log.info("[YouTube] Videos detail response length: {}", vRaw != null ? vRaw.length() : 0);

            VideosResponse vr = mapper().readValue(vRaw, VideosResponse.class);
            if (vr == null || vr.items == null || vr.items.length == 0) {
                log.warn("[YouTube] No video details found");
                return Optional.empty();
            }

            log.info("[YouTube] Found {} video details", vr.items.length);

            // 3) 검색 순서를 유지하며 'minSeconds 이상'인 첫 영상 선택
            Map<String, VideoItem> byId = new HashMap<>();
            for (VideoItem v : vr.items) {
                if (v != null && v.id != null) {
                    byId.put(v.id, v);
                    log.info("[YouTube] Video detail - id: {}, title: {}, duration: {}",
                            v.id,
                            v.snippet != null ? v.snippet.title : "null",
                            v.contentDetails != null ? v.contentDetails.duration : "null");
                }
            }

            for (Item it : sr.items) {
                if (it == null || it.id == null || it.id.videoId == null) continue;

                VideoItem v = byId.get(it.id.videoId);
                if (v == null || v.contentDetails == null) continue;

                long secs = parseDurationSec(v.contentDetails.duration); // ISO8601 → seconds
                log.info("[YouTube] Checking video {} - duration: {} seconds (min required: {})",
                        it.id.videoId, secs, minSeconds);

                if (secs >= minSeconds) {
                    String vid = v.id;
                    String title = v.snippet != null ? v.snippet.title : null;
                    String thumb = (v.snippet != null && v.snippet.thumbnails != null)
                            ? firstThumb(v.snippet.thumbnails) : null;

                    log.info("[YouTube] SELECTED video - id: {}, title: {}, thumbnail: {}",
                            vid, title, thumb);

                    YoutubeVideo result = new YoutubeVideo(vid, title, thumb);
                    log.info("[YouTube] Created YoutubeVideo - videoId: {}, title: {}, url: {}, thumbnailUrl: {}",
                            result.getVideoId(), result.getTitle(), result.getUrl(), result.getThumbnailUrl());

                    return Optional.of(result);
                }
            }

            // 4) 없으면 원래 1순위로 fallback
            Item first = sr.items[0];
            if (first != null && first.id != null && first.id.videoId != null) {
                String vid = first.id.videoId;
                String title = (first.snippet != null) ? first.snippet.title : null;
                String thumb = (first.snippet != null && first.snippet.thumbnails != null) ? firstThumb(first.snippet.thumbnails) : null;

                log.info("[YouTube] FALLBACK to first result - id: {}, title: {}, thumbnail: {}",
                        vid, title, thumb);

                YoutubeVideo result = new YoutubeVideo(vid, title, thumb);
                log.info("[YouTube] Created fallback YoutubeVideo - videoId: {}, title: {}, url: {}, thumbnailUrl: {}",
                        result.getVideoId(), result.getTitle(), result.getUrl(), result.getThumbnailUrl());

                return Optional.of(result);
            }

            log.warn("[YouTube] No valid results found even for fallback");
            return Optional.empty();

        } catch (Exception e) {
            log.error("[YouTube] Search failed for query: '{}'", query, e);
            return Optional.empty();
        }
    }

    private long parseDurationSec(String iso8601) {
        if (iso8601 == null) return 0L;
        try {
            long duration = java.time.Duration.parse(iso8601).getSeconds();
            log.debug("[YouTube] Parsed duration '{}' to {} seconds", iso8601, duration);
            return duration;
        } catch (Exception e) {
            log.warn("[YouTube] Failed to parse duration: {}", iso8601, e);
            return 0L;
        }
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