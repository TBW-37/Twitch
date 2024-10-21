package com.laioffer.twitch.recommendation;


import com.laioffer.twitch.db.entity.ItemEntity;
import com.laioffer.twitch.db.entity.UserEntity;
import com.laioffer.twitch.external.TwitchService;
import com.laioffer.twitch.external.model.Clip;
import com.laioffer.twitch.external.model.Stream;
import com.laioffer.twitch.external.model.Video;
import com.laioffer.twitch.favorite.FavoriteService;
import com.laioffer.twitch.model.TypeGroupedItemList;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Service
public class RecommendationService {


    private static final int MAX_GAME_SEED = 3;

    //maximum number for recommending videos in each category
    private static final int PER_PAGE_ITEM_SIZE = 20;


    private final TwitchService twitchService;
    private final FavoriteService favoriteService;


    public RecommendationService(TwitchService twitchService, FavoriteService favoriteService) {
        this.twitchService = twitchService;
        this.favoriteService = favoriteService;
    }

    @Cacheable("recommend_items")
    public TypeGroupedItemList recommendItems(UserEntity userEntity) {
        List<String> gameIds;

        //using set to ensure uniqueness
        Set<String> exclusions = new HashSet<>();

        //if the user hasn't register
        if (userEntity == null) {
            gameIds  = twitchService.getTopGameIds();
        } else {
            List<ItemEntity> items = favoriteService.getFavoriteItems(userEntity);

            //if the user registered but hasn't like anything
            if (items.isEmpty()) {
                gameIds = twitchService.getTopGameIds();
            } else {
                Set<String> uniqueGameIds = new HashSet<>();
                for (ItemEntity item : items) {

                    //to prevent from adding the same game from different streamers
                    uniqueGameIds.add(item.gameId());

                    //to prevent from recommending videos the users have already seen
                    exclusions.add(item.twitchId());
                }

                //once done, convert set to arraylist
                gameIds = new ArrayList<>(uniqueGameIds);
            }
        }


        int gameSize = Math.min(gameIds.size(), MAX_GAME_SEED);
        int perGameListSize = PER_PAGE_ITEM_SIZE / gameSize;


        List<ItemEntity> streams = recommendStreams(gameIds, exclusions);
        List<ItemEntity> clips = recommendClips(gameIds.subList(0, gameSize), perGameListSize, exclusions);
        List<ItemEntity> videos = recommendVideos(gameIds.subList(0, gameSize), perGameListSize, exclusions);


        return new TypeGroupedItemList(streams, videos, clips);
    }

    private List<ItemEntity> recommendStreams(List<String> gameIds, Set<String> exclusions) {

        //getStreams function accepts a list so we can directly feed it gameIds and use PER_PAGE_ITEM_SIZE
        List<Stream> streams = twitchService.getStreams(gameIds, PER_PAGE_ITEM_SIZE);
        List<ItemEntity> resultItems = new ArrayList<>();
        for (Stream stream: streams) {

            //ensure we are recommending new videos
            if (!exclusions.contains(stream.id())) {
                resultItems.add(new ItemEntity(stream));
            }
        }
        return resultItems;
    }


    private List<ItemEntity> recommendVideos(List<String> gameIds, int perGameListSize, Set<String> exclusions) {
        List<ItemEntity> resultItems = new ArrayList<>();
        for (String gameId : gameIds) {
            List<Video> listPerGame = twitchService.getVideos(gameId, perGameListSize);
            for (Video video : listPerGame) {
                if (!exclusions.contains(video.id())) {
                    resultItems.add(new ItemEntity(gameId, video));
                }
            }
        }
        return resultItems;
    }


    private List<ItemEntity> recommendClips(List<String> gameIds, int perGameListSize, Set<String> exclusions) {
        List<ItemEntity> resultItem = new ArrayList<>();
        for (String gameId : gameIds) {
            List<Clip> listPerGame = twitchService.getClips(gameId, perGameListSize);
            for (Clip clip : listPerGame) {
                if (!exclusions.contains(clip.id())) {
                    resultItem.add(new ItemEntity(clip));
                }
            }
        }
        return resultItem;
    }
}

