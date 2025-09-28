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
import java.util.*;
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
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[YouTube] API key is missing");
            return Optional.empty();
        }

        try {
            // API 키 일부 로그 (디버깅용)
            log.info("[YouTube] Using API Key: {}...", apiKey.substring(0, Math.min(10, apiKey.length())));

            // 검색어 개선
            String improvedQuery = "임산부 " + query + " 요가 운동 스트레칭";
            log.info("[YouTube] Search query: {}", improvedQuery);

            String sUrl = UriComponentsBuilder
                    .fromHttpUrl("https://www.googleapis.com/youtube/v3/search")
                    .queryParam("part", "snippet")
                    .queryParam("type", "video")
                    .queryParam("maxResults", 15) // 더 많은 결과로 필터링 옵션 증가
                    .queryParam("order", "relevance")
                    .queryParam("regionCode", "KR") // 한국 지역 코드
                    .queryParam("relevanceLanguage", "ko") // 한국어
                    .queryParam("safeSearch", "moderate") // 안전 검색
                    .queryParam("videoDefinition", "any") // HD/SD 모두 포함
                    .queryParam("videoDuration", "medium") // 중간 길이 영상 (4-20분)
                    .queryParam("q", improvedQuery)
                    .queryParam("key", apiKey)
                    .encode(StandardCharsets.UTF_8)
                    .toUriString();

            log.info("[YouTube] Request URL: {}", sUrl);

            // 요청 전 잠시 대기 (API 제한 방지)
            Thread.sleep(100);

            String raw = rest.get()
                    .uri(sUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            log.info("[YouTube] Response length: {} characters", raw != null ? raw.length() : 0);
            log.info("[YouTube] Response preview: {}",
                    raw != null ? raw.substring(0, Math.min(200, raw.length())) + "..." : "null");

            SearchResponse sr = mapper().readValue(raw, SearchResponse.class);
            if (sr == null || sr.items == null || sr.items.length == 0) {
                log.warn("[YouTube] No search results found");
                return Optional.empty();
            }

            log.info("[YouTube] Found {} search results", sr.items.length);

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

            log.info("[YouTube] Video IDs to check: {}", ids);

            String vUrl = "https://www.googleapis.com/youtube/v3/videos"
                    + "?part=snippet,contentDetails&id=" + ids + "&key=" + apiKey;

            String vRaw = rest.get()
                    .uri(vUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            VideosResponse vr = mapper().readValue(vRaw, VideosResponse.class);
            if (vr == null || vr.items == null || vr.items.length == 0) {
                log.warn("[YouTube] No video details found");
                return Optional.empty();
            }

            // 검색 순서를 유지하며 'minSeconds 이상'인 첫 영상 선택
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
                log.info("[YouTube] Video {} duration: {} seconds", it.id.videoId, secs);

                // 임산부 관련 키워드 체크 (추가 필터링)
                String title = v.snippet != null ? v.snippet.title : "";
                String description = v.snippet != null ? v.snippet.description : "";
                boolean isPregnancyRelated = containsPregnancyKeywords(title + " " + description);

                if (secs >= minSeconds && isPregnancyRelated) {
                    String vid = v.id;
                    String videoTitle = v.snippet != null ? v.snippet.title : null;
                    String thumb = (v.snippet != null && v.snippet.thumbnails != null)
                            ? firstThumb(v.snippet.thumbnails) : null;

                    log.info("[YouTube] Selected video: {} - {}", vid, videoTitle);
                    return Optional.of(new YoutubeVideo(vid, videoTitle, thumb));
                }
            }

            // 조건에 맞는 영상이 없으면 첫 번째 영상 반환 (기존 로직)
            Item first = sr.items[0];
            if (first != null && first.id != null) {
                String vid = first.id.videoId;
                String title = (first.snippet != null) ? first.snippet.title : null;
                String thumb = (first.snippet != null && first.snippet.thumbnails != null)
                        ? firstThumb(first.snippet.thumbnails) : null;

                log.info("[YouTube] Fallback to first result: {} - {}", vid, title);
                return Optional.of(new YoutubeVideo(vid, title, thumb));
            }

            return Optional.empty();

        } catch (Exception e) {
            log.error("[YouTube] Search failed for query: {}", query, e);
            return Optional.empty();
        }
    }

    private boolean containsPregnancyKeywords(String text) {
        if (text == null) return false;
        String lowerText = text.toLowerCase();
        return lowerText.contains("임산부") ||
                lowerText.contains("임신") ||
                lowerText.contains("예비맘") ||
                lowerText.contains("프레그넌시") ||
                lowerText.contains("pregnancy") ||
                lowerText.contains("prenatal");
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

