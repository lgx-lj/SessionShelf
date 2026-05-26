package com.sessionshelf.service;

import com.sessionshelf.dao.FavoriteProjectDAO;

import java.sql.SQLException;
import java.util.Set;

/**
 * 收藏项目业务服务
 */
public class FavoriteProjectService {

    private final FavoriteProjectDAO dao;

    public FavoriteProjectService() {
        this.dao = new FavoriteProjectDAO();
    }

    public void add(String projectName) throws SQLException { dao.add(projectName); }
    public void remove(String projectName) throws SQLException { dao.remove(projectName); }
    public boolean isFavorite(String projectName) throws SQLException { return dao.isFavorite(projectName); }
    public Set<String> getAll() throws SQLException { return dao.getAll(); }

    public boolean toggle(String projectName) throws SQLException {
        if (dao.isFavorite(projectName)) {
            dao.remove(projectName);
            return false;
        } else {
            dao.add(projectName);
            return true;
        }
    }
}
