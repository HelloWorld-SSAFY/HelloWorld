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
            // 더 구체적인 검색어로 개선
            String improvedQuery;
            if (query.contains("임산부")) {
                improvedQuery = "\"임산부 요가\" OR \"임산부 스트레칭\" OR \"prenatal yoga\"";
            } else {
                improvedQuery = "\"" + query + "\" 임산부 요가 스트레칭";
            }

            log.info("[YouTube] Original query: '{}', Improved query: '{}'", query, improvedQuery);

            String sUrl = UriComponentsBuilder
                    .fromHttpUrl("https://www.googleapis.com/youtube/v3/search")
                    .queryParam("part", "snippet")
                    .queryParam("type", "video")
                    .queryParam("maxResults", 15)   // 더 많은 결과로 필터링 옵션 증가
                    .queryParam("order", "relevance")
                    .queryParam("videoCategoryId", "26") // Howto & Style 카테고리
                    .queryParam("safeSearch", "moderate") // 안전 검색
                    .queryParam("q", improvedQuery)
                    .queryParam("key", apiKey)
                    .encode(StandardCharsets.UTF_8)
                    .toUriString();

            log.info("[YouTube] Search URL: {}", sUrl);

            String raw = rest.get().uri(sUrl).accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(String.class);

            log.info("[YouTube] Raw response length: {}", raw != null ? raw.length() : 0);

            SearchResponse sr = mapper().readValue(raw, SearchResponse.class);
            if (sr == null || sr.items == null || sr.items.length == 0) {
                log.warn("[YouTube] No search results found after parsing");
                return Optional.empty();
            }

            log.info("[YouTube] Parsed {} search results", sr.items.length);

            // 모든 파싱된 결과 상세 로깅
            for (int i = 0; i < sr.items.length; i++) {
                Item item = sr.items[i];
                String videoId = (item != null && item.id != null) ? item.id.videoId : "null";
                String title = (item != null && item.snippet != null) ? item.snippet.title : "null";
                log.info("[YouTube] Item[{}] - videoId: {}, title: {}", i, videoId, title);
            }

            // 상세 조회로 길이 확인
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

            String vRaw = rest.get().uri(vUrl).accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(String.class);

            VideosResponse vr = mapper().readValue(vRaw, VideosResponse.class);
            if (vr == null || vr.items == null || vr.items.length == 0) {
                log.warn("[YouTube] No video details found");
                return Optional.empty();
            }

            log.info("[YouTube] Found {} video details", vr.items.length);

            // 검색 순서를 유지하며 임산부 관련 + 최소 길이 조건 확인
            Map<String, VideoItem> byId = new HashMap<>();
            for (VideoItem v : vr.items) {
                if (v != null && v.id != null) {
                    byId.put(v.id, v);
                }
            }

            for (Item it : sr.items) {
                if (it == null || it.id == null || it.id.videoId == null) continue;

                VideoItem v = byId.get(it.id.videoId);
                if (v == null || v.contentDetails == null) continue;

                long secs = parseDurationSec(v.contentDetails.duration);
                boolean isPregnancyRelated = isPregnancyRelated(v);

                log.info("[YouTube] Checking video {} - duration: {} seconds, pregnancy related: {}",
                        it.id.videoId, secs, isPregnancyRelated);

                if (secs >= minSeconds && isPregnancyRelated) {
                    String vid = v.id;
                    String title = v.snippet != null ? v.snippet.title : null;
                    String thumb = (v.snippet != null && v.snippet.thumbnails != null)
                            ? firstThumb(v.snippet.thumbnails) : null;

                    log.info("[YouTube] SELECTED video - id: {}, title: {}, thumbnail: {}",
                            vid, title, thumb);

                    return Optional.of(new YoutubeVideo(vid, title, thumb));
                }
            }

            // 임산부 관련 영상이 없으면 길이 조건만 확인
            log.warn("[YouTube] No pregnancy-related videos found, checking by duration only");
            for (Item it : sr.items) {
                if (it == null || it.id == null || it.id.videoId == null) continue;

                VideoItem v = byId.get(it.id.videoId);
                if (v == null || v.contentDetails == null) continue;

                long secs = parseDurationSec(v.contentDetails.duration);
                if (secs >= minSeconds) {
                    String vid = v.id;
                    String title = v.snippet != null ? v.snippet.title : null;
                    String thumb = (v.snippet != null && v.snippet.thumbnails != null)
                            ? firstThumb(v.snippet.thumbnails) : null;

                    log.info("[YouTube] FALLBACK SELECTED by duration - id: {}, title: {}", vid, title);
                    return Optional.of(new YoutubeVideo(vid, title, thumb));
                }
            }

            // 최종 fallback: 첫 번째 결과
            Item first = sr.items[0];
            if (first != null && first.id != null && first.id.videoId != null) {
                String vid = first.id.videoId;
                String title = (first.snippet != null) ? first.snippet.title : null;
                String thumb = (first.snippet != null && first.snippet.thumbnails != null)
                        ? firstThumb(first.snippet.thumbnails) : null;

                log.warn("[YouTube] ULTIMATE FALLBACK to first result - id: {}, title: {}", vid, title);
                return Optional.of(new YoutubeVideo(vid, title, thumb));
            }

            log.warn("[YouTube] No valid results found");
            return Optional.empty();

        } catch (Exception e) {
            log.error("[YouTube] Search failed for query: '{}'", query, e);
            return Optional.empty();
        }
    }

    private boolean isPregnancyRelated(VideoItem video) {
        if (video.snippet == null) return false;

        String title = video.snippet.title != null ? video.snippet.title.toLowerCase() : "";
        String description = video.snippet.description != null ? video.snippet.description.toLowerCase() : "";
        String text = title + " " + description;

        boolean related = text.contains("임산부") ||
                text.contains("임신") ||
                text.contains("prenatal") ||
                text.contains("pregnancy") ||
                text.contains("요가") ||
                text.contains("yoga") ||
                text.contains("스트레칭") ||
                text.contains("stretching") ||
                text.contains("운동") ||
                text.contains("exercise");

        // IT, 낚시, 정치 등 관련 없는 키워드 제외
        boolean excluded = text.contains("network") ||
                text.contains("boot") ||
                text.contains("ipv6") ||
                text.contains("낚시") ||
                text.contains("fishing") ||
                text.contains("대통령") ||
                text.contains("정치") ||
                text.contains("헌법") ||
                text.contains("재판") ||
                text.contains("빈대") ||
                text.contains("여성가족부");

        return related && !excluded;
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
    @JsonIgnoreProperties(ignoreUnknown = true) static class Snippet { String title; String description; Thumbnails thumbnails; }
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