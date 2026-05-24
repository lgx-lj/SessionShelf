package com.sessionshelf.service;

import com.sessionshelf.dao.FavoriteDAO;
import com.sessionshelf.model.Session;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * 收藏会话业务服务
 */
public class FavoriteService {

    private final FavoriteDAO favoriteDAO;

    public FavoriteService() {
        this.favoriteDAO = new FavoriteDAO();
    }

    public void addFavorite(String sessionId) throws SQLException {
        favoriteDAO.addFavorite(sessionId);
    }

    public void removeFavorite(String sessionId) throws SQLException {
        favoriteDAO.removeFavorite(sessionId);
    }

    public boolean isFavorite(String sessionId) throws SQLException {
        return favoriteDAO.isFavorite(sessionId);
    }

    public Set<String> getAllFavoriteIds() throws SQLException {
        return favoriteDAO.getAllFavoriteIds();
    }

    public List<Session> getFavoriteSessions() throws SQLException {
        return favoriteDAO.getFavoriteSessions();
    }

    /**
     * 切换收藏状态，返回切换后的收藏状态
     */
    public boolean toggleFavorite(String sessionId) throws SQLException {
        if (favoriteDAO.isFavorite(sessionId)) {
            favoriteDAO.removeFavorite(sessionId);
            return false;
        } else {
            favoriteDAO.addFavorite(sessionId);
            return true;
        }
    }
}
