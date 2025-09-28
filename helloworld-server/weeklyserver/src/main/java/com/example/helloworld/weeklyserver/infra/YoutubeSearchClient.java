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

    // YoutubeSearchClient.java (핵심 변경)
    public Optional<YoutubeVideo> searchFirst(String query) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[YouTube] API KEY missing");
            return Optional.empty();
        }
        try {
            // 검색 옵션 강화 + TopN
            int max = 8;
            String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://www.googleapis.com/youtube/v3/search"
                    + "?part=snippet&type=video"
                    + "&maxResults=" + max
                    + "&regionCode=KR"
                    + "&relevanceLanguage=ko"
                    + "&safeSearch=moderate"
                    + "&videoEmbeddable=true"
                    + "&videoDuration=medium"  // shorts 회피 (필요에 따라 long)
                    + "&q=" + q
                    + "&key=" + apiKey;

            String raw = restClient.get().uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            log.debug("[YouTube] raw={}", raw);

            ObjectMapper mapper = new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

            SearchResponse res = mapper.readValue(raw, SearchResponse.class);
            if (res == null || res.items == null || res.items.length == 0) {
                log.warn("[YouTube] No items for query: {}", query);
                return Optional.empty();
            }

            // 후보들 중에서 점수 가장 높은 영상 선택
            Optional<Item> best = pickBest(res.items, query);
            if (best.isEmpty() || best.get().id == null || best.get().id.videoId == null) {
                log.warn("[YouTube] No good match for query: {}", query);
                return Optional.empty();
            }

            Item item = best.get();
            String vid = item.id.videoId;
            String title = item.snippet != null ? item.snippet.title : null;
            String thumb = (item.snippet != null && item.snippet.thumbnails != null) ? firstThumb(item.snippet.thumbnails) : null;

            return Optional.of(new YoutubeVideo(vid, title, thumb));
        } catch (Exception e) {
            log.warn("[YouTube] search failed", e);
            return Optional.empty();
        }
    }

    // 간단한 스코어링(한글/영문 임신 관련 키워드 가중치 + 원 쿼리 토큰 매칭)
    private Optional<Item> pickBest(Item[] items, String query) {
        String q = query.toLowerCase();
        String[] musts = {"임산부", "임신", "산모", "prenatal", "pregnant"};
        String[] hints  = tokenize(q); // 원 쿼리 토큰

        Item best = null;
        int bestScore = Integer.MIN_VALUE;

        for (Item it : items) {
            if (it == null || it.snippet == null) continue;
            String t = safeLower(it.snippet.title);

            int score = 0;
            for (String m : musts) if (t.contains(m)) score += 5; // 핵심 키워드
            for (String h : hints) if (h.length() >= 2 && t.contains(h)) score += 2; // 쿼리 토큰

            // 너무 동떨어진(핵심키워드 전혀 없음) 결과는 패스
            if (score < 3) continue;

            if (score > bestScore) { bestScore = score; best = it; }
        }
        // 그래도 없으면 첫 번째로 fallback
        return (best != null) ? Optional.of(best) : Optional.ofNullable(items[0]);
    }

    private String[] tokenize(String s) {
        return safeLower(s).replaceAll("[^0-9a-zA-Z가-힣\\s]", " ").split("\\s+");
    }
    private String safeLower(String s) { return s == null ? "" : s.toLowerCase(); }



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
