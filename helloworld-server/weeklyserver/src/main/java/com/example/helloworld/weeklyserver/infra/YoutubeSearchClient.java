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

    // === Public API ===
    public Optional<YoutubeVideo> searchFirst(String query) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[YouTube] API KEY missing");
            return Optional.empty();
        }
        try {
            // 1) Top-N 검색
            int max = 10;
            String url = "https://www.googleapis.com/youtube/v3/search"
                    + "?part=snippet&type=video"
                    + "&maxResults=" + max
                    + "&regionCode=KR"
                    + "&relevanceLanguage=ko"
                    + "&safeSearch=moderate"
                    + "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&key=" + apiKey;

            String raw = rest.get().uri(url).accept(MediaType.APPLICATION_JSON).retrieve().body(String.class);
            SearchResponse sr = mapper().readValue(raw, SearchResponse.class);
            if (sr.items == null || sr.items.length == 0) return Optional.empty();

            // 2) 상세조회 (videos.list)
            String ids = Arrays.stream(sr.items)
                    .map(it -> it != null && it.id != null ? it.id.videoId : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(","));
            if (ids.isEmpty()) return Optional.empty();

            String vurl = "https://www.googleapis.com/youtube/v3/videos"
                    + "?part=snippet,contentDetails"
                    + "&id=" + ids
                    + "&key=" + apiKey;

            String vraw = rest.get().uri(vurl).accept(MediaType.APPLICATION_JSON).retrieve().body(String.class);
            VideosResponse vr = mapper().readValue(vraw, VideosResponse.class);
            if (vr.items == null || vr.items.length == 0) return Optional.empty();

            // 3) 스코어링
            VideoItem best = pickBest(vr.items, query);
            if (best == null || best.id == null) return Optional.empty();

            String vid = best.id;
            String title = best.snippet != null ? best.snippet.title : null;
            String thumb = (best.snippet != null && best.snippet.thumbnails != null) ? firstThumb(best.snippet.thumbnails) : null;
            return Optional.of(new YoutubeVideo(vid, title, thumb));
        } catch (Exception e) {
            log.warn("[YouTube] search failed", e);
            return Optional.empty();
        }
    }

    // === scoring ===
    private VideoItem pickBest(VideoItem[] items, String query) {
        String[] must = {"임산부","임신","산모","prenatal","pregnant","요가","스트레칭","운동"};
        String[] bad  = {"룩","하울","코디","패션","자켓","니트","스커트","원피스","쇼핑","언박싱"};
        String[] toks = tokenize(query);

        VideoItem best = null;
        int bestScore = Integer.MIN_VALUE;

        for (VideoItem v : items) {
            if (v == null || v.snippet == null) continue;
            String title = lc(v.snippet.title);
            String desc  = lc(v.snippet.description);
            List<String> tags = v.snippet.tags != null ? v.snippet.tags.stream().map(this::lc).toList() : List.of();

            int score = 0;

            String all = title + " " + desc + " " + String.join(" ", tags);
            for (String m : must) if (all.contains(lc(m))) score += 5;
            for (String t : toks)  if (t.length() >= 2 && all.contains(t)) score += 2;

            // 언어 가점
            String lang = v.snippet.defaultAudioLanguage != null ? v.snippet.defaultAudioLanguage : v.snippet.defaultLanguage;
            if (lang != null && lang.toLowerCase().startsWith("ko")) score += 3;

            // 카테고리 가점 (Howto 26, Sports 17, Education 27)
            if ("17".equals(v.snippet.categoryId) || "26".equals(v.snippet.categoryId) || "27".equals(v.snippet.categoryId)) score += 2;

            // 길이 8~40분 가점 (쇼츠/초장편 회피)
            long secs = parseDurationSec(v.contentDetails != null ? v.contentDetails.duration : null);
            if (secs >= 480 && secs <= 2400) score += 2;

            // 금지어 강한 패널티
            for (String b : bad) if (title.contains(lc(b))) score -= 8;

            if (score > bestScore) { bestScore = score; best = v; }
        }
        return best != null ? best : items[0]; // 최후 fallback
    }

    private long parseDurationSec(String iso8601) {
        if (iso8601 == null) return 0L;
        return java.time.Duration.parse(iso8601).getSeconds();
    }
    private String[] tokenize(String s){ return lc(s).replaceAll("[^0-9a-zA-Z가-힣\\s]"," ").split("\\s+"); }
    private String lc(String s){ return s==null? "": s.toLowerCase(); }

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
    private ObjectMapper mapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
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

    @Getter
    public static class YoutubeVideo {
        private final String videoId, title, url, thumbnailUrl;
        public YoutubeVideo(String videoId, String title, String thumbnailUrl) {
            this.videoId = videoId; this.title = title;
            this.url = "https://www.youtube.com/watch?v=" + videoId;
            this.thumbnailUrl = thumbnailUrl;
        }
    }
}

