package com.sessionshelf.controller;

import com.sessionshelf.config.AppConfig;
import com.sessionshelf.model.Session;
import com.sessionshelf.model.enums.SourceType;
import com.sessionshelf.service.ExportService;
import com.sessionshelf.service.FavoriteService;
import com.sessionshelf.service.FavoriteProjectService;
import com.sessionshelf.service.SessionService;
import com.sessionshelf.service.SyncService;
import com.sessionshelf.util.ClipboardUtil;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import netscape.javascript.JSObject;

import java.io.File;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 主窗口控制器
 * 支持多路径扫描、可拉伸/可收起侧边栏、嵌套 SplitPane 布局
 * 目录树根据 workingDirectory 生成真实项目路径
 */
public class MainController implements Initializable {

    @FXML private BorderPane rootPane;
    @FXML private VBox leftPanel;
    @FXML private VBox centerPanel;
    @FXML private VBox rightPanel;
    @FXML private Button toggleSidebarBtn;
    @FXML private Button caseSensitiveBtn;

    @FXML private TextField searchField;
    @FXML private TreeView<PathNode> folderTree;

    @FXML private ComboBox<String> sourceFilter;
    @FXML private ComboBox<String> sortOrder;
    @FXML private Label sessionCount;
    @FXML private ListView<Session> sessionList;

    @FXML private Label sessionTitle;
    @FXML private Label sessionSource;
    @FXML private Label sessionModel;
    @FXML private Label sessionTime;
    @FXML private WebView contentWebView;

    @FXML private HBox searchNavBar;
    @FXML private Button prevMatchBtn;
    @FXML private Button nextMatchBtn;
    @FXML private Label matchCountLabel;

    @FXML private Label statusLabel;
    @FXML private Label dataSourceStatus;

    private SessionService sessionService;
    private SyncService syncService;
    private ExportService exportService;
    private AppConfig config;

    private ObservableList<Session> sessions;
    private List<Session> allSessions;

    private Session selectedSession;
    private String selectedFolderPath;

    // 搜索功能
    private boolean caseSensitive = false;
    private Timer searchDebounceTimer;
    private static final long SEARCH_DEBOUNCE_MS = 300;
    // 会话内容缓存：sessionId → 全文内容
    private Map<String, String> contentCache = new HashMap<>();
    // 当前搜索关键词（原始输入）
    private String currentSearchText = "";

    // 收藏功能
    private FavoriteService favoriteService;
    private Set<String> favoriteSessionIds = new HashSet<>();
    private FavoriteProjectService favoriteProjectService;
    private Set<String> favoriteProjectNames = new HashSet<>();

    // 主题
    private String currentTheme = "light";
    private static final String THEME_KEY = "app.theme";
    private static final String LIGHT_CSS = "/css/style.css";
    private static final String DARK_CSS = "/css/dark.css";

    private boolean sidebarCollapsed = false;
    private double savedLeftDivider = 0.22;
    private double savedRightDivider = 0.55;

    // 嵌套分割面板
    private SplitPane mainSplitPane;
    private SplitPane contentSplitPane;

    // 扫描路径配置
    private List<String> scanPaths = new ArrayList<>();
    private static final String SCAN_PATHS_KEY = "scan.paths";
    private static final String LAYOUT_LEFT_KEY = "layout.left.divider";
    private static final String LAYOUT_RIGHT_KEY = "layout.right.divider";

    private static final String NODE_ALL = "ALL";
    private static final String NODE_UNCLASSIFIED = "UNCLASSIFIED";
    private static final String NODE_FAVORITE = "FAVORITE";
    private static final String NODE_FAV_PROJECT = "FAV_PROJECT";

    /**
     * 目录树节点
     */
    public static class PathNode {
        private String nodeType;
        private String displayName;
        private String fullPath;
        private String tooltip;

        public PathNode(String nodeType, String displayName, String fullPath, String tooltip) {
            this.nodeType = nodeType;
            this.displayName = displayName;
            this.fullPath = fullPath;
            this.tooltip = tooltip;
        }

        public String getNodeType() { return nodeType; }
        public String getDisplayName() { return displayName; }
        public String getFullPath() { return fullPath; }
        public String getTooltip() { return tooltip; }

        @Override
        public String toString() { return displayName; }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        config = AppConfig.getInstance();
        sessionService = new SessionService();
        favoriteService = new FavoriteService();
        favoriteProjectService = new FavoriteProjectService();
        syncService = new SyncService();
        exportService = new ExportService(syncService);

        sessions = FXCollections.observableArrayList();
        allSessions = new ArrayList<>();

        sessionList.setItems(sessions);

        // 加载扫描路径配置
        loadScanPaths();

        // 恢复布局位置
        savedLeftDivider = Double.parseDouble(config.getString(LAYOUT_LEFT_KEY, "0.22"));
        savedRightDivider = Double.parseDouble(config.getString(LAYOUT_RIGHT_KEY, "0.55"));

        initFilters();
        initFolderTree();
        initSearchField();
        buildLayout();
        loadData();

        sessionList.setCellFactory(param -> new SessionListCell());

        // 会话列表右键菜单
        initSessionContextMenu();

        sessionList.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, val) -> onSessionSelected(val));

        updateDataSourceStatus();

        // 加载主题设置
        currentTheme = config.getString(THEME_KEY, "light");
        Platform.runLater(this::applyTheme);
    }

    /**
     * 构建嵌套 SplitPane 布局
     * 外层：左侧面板 | 中间+右侧整体
     * 内层：中间会话列表 | 右侧详情窗口
     */
    private void buildLayout() {
        // 修复：FXML 中多个面板竞争 BorderPane 的 center，后加载的会把前面的 managed 置为 false
        // 必须在放入 SplitPane 前显式恢复所有面板的可见性
        leftPanel.setManaged(true);
        leftPanel.setVisible(true);
        centerPanel.setManaged(true);
        centerPanel.setVisible(true);
        rightPanel.setManaged(true);
        rightPanel.setVisible(true);

        // 内层：中间面板 + 右侧面板
        contentSplitPane = new SplitPane(centerPanel, rightPanel);
        contentSplitPane.setDividerPositions(savedRightDivider);
        SplitPane.setResizableWithParent(centerPanel, true);
        SplitPane.setResizableWithParent(rightPanel, true);

        // 监听内层分割位置变化，持久化
        contentSplitPane.getDividers().get(0).positionProperty().addListener((obs, old, val) -> {
            savedRightDivider = val.doubleValue();
        });

        // 外层：左侧面板 + 内层 SplitPane
        mainSplitPane = new SplitPane(leftPanel, contentSplitPane);
        mainSplitPane.setDividerPositions(savedLeftDivider);
        SplitPane.setResizableWithParent(leftPanel, true);
        SplitPane.setResizableWithParent(contentSplitPane, true);

        // 监听外层分割位置变化，持久化
        mainSplitPane.getDividers().get(0).positionProperty().addListener((obs, old, val) -> {
            if (!sidebarCollapsed) {
                savedLeftDivider = val.doubleValue();
            }
        });

        // 替换 BorderPane 的 center
        rootPane.setCenter(mainSplitPane);
    }

    /**
     * 收起/展开侧边栏
     */
    @FXML
    private void onToggleSidebar() {
        if (sidebarCollapsed) {
            mainSplitPane.setDividerPositions(savedLeftDivider);
            leftPanel.setVisible(true);
            leftPanel.setManaged(true);
            toggleSidebarBtn.setText("◀");
            sidebarCollapsed = false;
        } else {
            savedLeftDivider = mainSplitPane.getDividers().get(0).getPosition();
            mainSplitPane.setDividerPositions(0.0);
            leftPanel.setVisible(false);
            leftPanel.setManaged(false);
            toggleSidebarBtn.setText("▶");
            sidebarCollapsed = true;
        }
    }

    // ============ 扫描路径配置 ============

    private void loadScanPaths() {
        String saved = config.getString(SCAN_PATHS_KEY, "");
        if (saved.isEmpty()) {
            scanPaths.add(System.getProperty("user.home"));
        } else {
            String[] paths = saved.split("\\|");
            for (String p : paths) {
                if (!p.trim().isEmpty()) {
                    scanPaths.add(p.trim());
                }
            }
        }
    }

    private void saveScanPaths() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < scanPaths.size(); i++) {
            if (i > 0) sb.append("|");
            sb.append(scanPaths.get(i));
        }
        config.setString(SCAN_PATHS_KEY, sb.toString());
        config.save();
    }

    /**
     * 保存布局分割位置到配置文件
     */
    public void saveLayoutPositions() {
        config.setString(LAYOUT_LEFT_KEY, String.valueOf(savedLeftDivider));
        config.setString(LAYOUT_RIGHT_KEY, String.valueOf(savedRightDivider));
        config.save();
    }

    // ============ 搜索框初始化（防抖 + 大小写切换） ============

    private void initSearchField() {
        // 恢复大小写敏感配置
        caseSensitive = config.getBoolean("search.case.sensitive", false);
        updateCaseSensitiveBtnStyle();

        // 输入防抖：300ms 后自动执行搜索
        searchField.textProperty().addListener((obs, oldText, newText) -> {
            if (searchDebounceTimer != null) {
                searchDebounceTimer.cancel();
            }
            searchDebounceTimer = new Timer(true);
            searchDebounceTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> doSearch(newText));
                }
            }, SEARCH_DEBOUNCE_MS);
        });
    }

    @FXML
    private void onToggleCaseSensitive() {
        caseSensitive = !caseSensitive;
        config.setBoolean("search.case.sensitive", caseSensitive);
        config.save();
        updateCaseSensitiveBtnStyle();
        // 立即用当前文本重新搜索
        doSearch(searchField.getText());
    }

    private void updateCaseSensitiveBtnStyle() {
        if (caseSensitive) {
            caseSensitiveBtn.setStyle("-fx-background-color:#4a90d9;-fx-text-fill:white;-fx-font-size:11px;-fx-font-weight:bold;");
            caseSensitiveBtn.setTooltip(new Tooltip("大小写敏感（点击切换）"));
        } else {
            caseSensitiveBtn.setStyle("");
            caseSensitiveBtn.setTooltip(new Tooltip("忽略大小写（点击切换）"));
        }
    }

    /**
     * 执行搜索（支持多关键词 AND 逻辑 + 内容全文搜索）
     */
    private void doSearch(String searchText) {
        currentSearchText = (searchText == null) ? "" : searchText.trim();
        filterSessions();
    }

    /**
     * 后台加载所有会话全文内容到缓存
     */
    private void buildContentCache(List<Session> sessionListData) {
        new Thread(() -> {
            Map<String, String> cache = new HashMap<>();
            int[] countHolder = {0};
            for (Session s : sessionListData) {
                try {
                    String content = syncService.readSessionContent(s);
                    if (content != null && !content.isEmpty()) {
                        cache.put(s.getSessionId(), content);
                        countHolder[0]++;
                    }
                } catch (Exception ignored) {}
            }
            contentCache = cache;
            Platform.runLater(() -> statusLabel.setText("内容索引完成：" + countHolder[0] + " 条"));
        }).start();
    }

    private void initFilters() {
        sourceFilter.setItems(FXCollections.observableArrayList(
                "全部来源", "Claude Code", "OpenAI Codex", "CC Switch"
        ));
        sourceFilter.setValue("全部来源");
        sourceFilter.setOnAction(e -> filterSessions());

        sortOrder.setItems(FXCollections.observableArrayList("最近恢复", "时间倒序", "时间正序"));
        sortOrder.setValue("最近恢复");
        sortOrder.setOnAction(e -> filterSessions());
    }

    // ============ 目录树（根据 workingDirectory 生成真实项目路径） ============

    private void initFolderTree() {
        TreeItem<PathNode> root = new TreeItem<>(new PathNode("ROOT", "根", null, null));
        root.setExpanded(true);
        folderTree.setRoot(root);
        folderTree.setShowRoot(false);

        // 默认节点：所有会话
        TreeItem<PathNode> allItem = new TreeItem<>(new PathNode(NODE_ALL, "所有会话", null, "显示全部会话"));
        allItem.setExpanded(true);

        // 收藏项目节点
        TreeItem<PathNode> favProjectItem = new TreeItem<>(new PathNode(NODE_FAV_PROJECT, "⭐ 收藏项目", null, "按工作目录最后一层分组，显示某项目下的全部会话"));
        root.getChildren().add(favProjectItem);

        // 收藏节点
        TreeItem<PathNode> favoriteItem = new TreeItem<>(new PathNode(NODE_FAVORITE, "⭐ 收藏会话", null, "显示所有收藏的会话"));
        root.getChildren().add(favoriteItem);
        root.getChildren().add(allItem);

        // 默认节点：未归档
        TreeItem<PathNode> unclassifiedItem = new TreeItem<>(new PathNode(NODE_UNCLASSIFIED, "未归档", null, "没有工作目录的会话"));
        root.getChildren().add(unclassifiedItem);

        // 设置单元格工厂：文本省略 + Tooltip
        folderTree.setCellFactory(tv -> new TreeCell<PathNode>() {
            @Override
            protected void updateItem(PathNode item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                } else {
                    setText(item.getDisplayName());
                    // 宽度不够时自动省略，悬停显示完整路径
                    setStyle("-fx-font-size: 12px;");
                    if (item.getFullPath() != null && !item.getFullPath().isEmpty()) {
                        setTooltip(new Tooltip(item.getFullPath()));
                    } else if (item.getTooltip() != null && !item.getTooltip().isEmpty()) {
                        setTooltip(new Tooltip(item.getTooltip()));
                    } else {
                        setTooltip(null);
                    }
                }
            }
        });

        // 目录树选择监听
        folderTree.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, val) -> onPathNodeSelected(val));
    }

    /**
     * 根据会话的 workingDirectory 构建目录树
     * 使用前缀树（Trie）方式：每条路径按目录组件逐级插入，天然去重、不会出现重复节点
     */
    private void buildPathTree(List<Session> sessionListData) {
        TreeItem<PathNode> root = folderTree.getRoot();

        // 保留默认的收藏项目、收藏会话、所有会话、未归档节点
        while (root.getChildren().size() > 4) {
            root.getChildren().remove(4);
        }
        TreeItem<PathNode> favProjectRoot = root.getChildren().get(0);
        TreeItem<PathNode> unclassifiedItem = root.getChildren().get(3);

        // === 第一步：按盘符收集所有会话的工作目录 ===
        Map<String, List<String[]>> driveToComponentPaths = new TreeMap<>();

        for (Session session : sessionListData) {
            String wd = session.getWorkingDirectory();
            if (wd == null || wd.trim().isEmpty()) continue;
            String normalized = normalizePath(wd.trim());
            String drive = extractDrive(normalized);
            if (drive == null) continue;

            // 拆成目录组件数组
            String afterDrive = normalized.substring(drive.length());
            if (afterDrive.startsWith(File.separator)) afterDrive = afterDrive.substring(1);
            if (afterDrive.endsWith(File.separator)) afterDrive = afterDrive.substring(0, afterDrive.length() - 1);
            if (afterDrive.isEmpty()) continue;

            String[] components = afterDrive.split(Pattern.quote(File.separator));
            driveToComponentPaths.computeIfAbsent(drive, k -> new ArrayList<>());
            driveToComponentPaths.get(drive).add(components);
        }

        System.out.println("[目录树] 共 " + driveToComponentPaths.size() + " 个盘符");

        // === 第二步：为每个盘符用 Trie 构建目录树 ===
        for (Map.Entry<String, List<String[]>> entry : driveToComponentPaths.entrySet()) {
            String drive = entry.getKey();
            List<String[]> componentPaths = entry.getValue();

            long driveCount = componentPaths.size();
            PathNode driveNode = new PathNode("PATH", drive, drive, drive + " 盘");
            TreeItem<PathNode> driveItem = new TreeItem<>(driveNode);
            driveItem.setExpanded(true);
            root.getChildren().add(driveItem);

            // Trie 核心：完整路径 → TreeItem 的映射
            Map<String, TreeItem<PathNode>> nodeMap = new HashMap<>();
            // 会话计数：完整路径 → 经过该路径的会话数
            Map<String, Integer> countMap = new HashMap<>();

            for (String[] components : componentPaths) {
                StringBuilder pathBuilder = new StringBuilder(drive);
                for (String component : components) {
                    pathBuilder.append(File.separator).append(component);
                    String fullPath = pathBuilder.toString();

                    // 累加计数（每条路径上的每一级都 +1）
                    countMap.merge(fullPath, 1, Integer::sum);

                    if (!nodeMap.containsKey(fullPath)) {
                        PathNode pathNode = new PathNode("PATH", component, fullPath, fullPath);
                        TreeItem<PathNode> pathItem = new TreeItem<>(pathNode);
                        nodeMap.put(fullPath, pathItem);

                        // 找父节点：取 fullPath 的父目录
                        String parentPath = new File(fullPath).getParent();
                        if (parentPath != null) {
                            parentPath = normalizePath(parentPath);
                        }

                        TreeItem<PathNode> parentItem = nodeMap.get(parentPath);
                        if (parentItem != null) {
                            parentItem.getChildren().add(pathItem);
                        } else {
                            driveItem.getChildren().add(pathItem);
                        }
                    }
                }
            }

            // 调试日志：打印 Trie 结构
            System.out.println("[目录树] " + drive + " 共 " + nodeMap.size() + " 个节点");
            for (Map.Entry<String, TreeItem<PathNode>> ne : nodeMap.entrySet()) {
                TreeItem<PathNode> ti = ne.getValue();
                int childCount = ti.getChildren().size();
                int sessionCount = countMap.getOrDefault(ne.getKey(), 0);
                String parentKey = new File(ne.getKey()).getParent();
                if (parentKey != null) parentKey = normalizePath(parentKey);
                System.out.println("  " + ne.getKey() + " → 父=" + parentKey
                        + " 子节点=" + childCount + " 会话=" + sessionCount);
            }

            // === 第三步：更新显示名（目录名 + 会话数） ===
            for (Map.Entry<String, TreeItem<PathNode>> ne : nodeMap.entrySet()) {
                TreeItem<PathNode> item = ne.getValue();
                int count = countMap.getOrDefault(ne.getKey(), 0);
                item.getValue().displayName = item.getValue().displayName + " (" + count + ")";
            }

            // 更新盘符会话数
            driveNode.displayName = drive + " (" + driveCount + ")";
        }

        // === 构建收藏项目子节点（每个收藏项目显示为一个按项目筛选的虚拟节点） ===
        favProjectRoot.getChildren().clear();
        // 按 workingDirectory 最后一层分组
        Map<String, Long> projectCounts = new TreeMap<>();
        for (Session s : sessionListData) {
            String wd = s.getWorkingDirectory();
            if (wd == null || wd.trim().isEmpty()) continue;
            String projectName = extractProjectNameFromPath(wd.trim());
            if (projectName == null || projectName.isEmpty()) continue;
            projectCounts.merge(projectName, 1L, Long::sum);
        }
        for (Map.Entry<String, Long> entry : projectCounts.entrySet()) {
            String pn = entry.getKey();
            long cnt = entry.getValue();
            boolean isFav = favoriteProjectNames.contains(pn);
            if (!isFav) continue; // 只显示已收藏的项目
            PathNode pnNode = new PathNode("PROJECT", pn + " (" + cnt + ")", pn, pn);
            TreeItem<PathNode> pnItem = new TreeItem<>(pnNode);
            favProjectRoot.getChildren().add(pnItem);
        }
        favProjectRoot.getValue().displayName = "⭐ 收藏项目 (" + favoriteProjectNames.size() + ")";
        favProjectRoot.setExpanded(true);

        // 更新收藏会话计数
        int favCount = (int) sessionListData.stream()
                .filter(s -> favoriteSessionIds.contains(s.getSessionId()))
                .count();
        root.getChildren().get(1).getValue().displayName = "⭐ 收藏会话 (" + favCount + ")";

        // 更新未归档计数
        long unclassifiedCount = sessionListData.stream()
                .filter(s -> s.getWorkingDirectory() == null || s.getWorkingDirectory().trim().isEmpty())
                .count();
        unclassifiedItem.getValue().displayName = "未归档 (" + unclassifiedCount + ")";
    }

    /**
     * 目录树节点选中事件
     */
    private void onPathNodeSelected(TreeItem<PathNode> item) {
        if (item == null || item.getValue() == null) {
            selectedFolderPath = null;
        } else {
            PathNode node = item.getValue();
            if (NODE_ALL.equals(node.getNodeType())) {
                selectedFolderPath = null;
            } else if (NODE_UNCLASSIFIED.equals(node.getNodeType())) {
                selectedFolderPath = "__UNCLASSIFIED__";
            } else if (NODE_FAVORITE.equals(node.getNodeType())) {
                selectedFolderPath = "__FAVORITE__";
            } else if (NODE_FAV_PROJECT.equals(node.getNodeType())) {
                selectedFolderPath = "__FAV_PROJECT__";
            } else if ("PROJECT".equals(node.getNodeType())) {
                // 按项目名筛选
                selectedFolderPath = "__PROJECT__:" + node.getFullPath();
            } else {
                selectedFolderPath = node.getFullPath();
            }
        }
        filterSessions();
    }

    /** 从工作目录路径提取最后一级文件夹名 */
    private String extractProjectNameFromPath(String wd) {
        if (wd == null) return null;
        String normalized = wd.replace("\\", "/");
        if (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < normalized.length() - 1) {
            return normalized.substring(lastSlash + 1);
        }
        // 如果是盘符根路径，用盘符作为项目名
        if (normalized.length() == 2 && normalized.endsWith(":")) return normalized;
        return normalized;
    }

    // ============ 会话列表右键菜单 ============

    /**
     * 切换会话收藏状态
     */
    private void toggleFavorite(Session session) {
        try {
            boolean nowFav = favoriteService.toggleFavorite(session.getSessionId());
            if (nowFav) {
                favoriteSessionIds.add(session.getSessionId());
                statusLabel.setText("已收藏: " + session.getTitle());
            } else {
                favoriteSessionIds.remove(session.getSessionId());
                statusLabel.setText("已取消收藏: " + session.getTitle());
            }
            // 刷新列表（更新星标显示）
            sessionList.refresh();
            // 刷新目录树收藏计数
            buildPathTree(allSessions);
        } catch (SQLException e) {
            showError("收藏操作失败", e.getMessage());
        }
    }

    private void toggleFavoriteProject(Session session) {
        String wd = session.getWorkingDirectory();
        if (wd == null || wd.trim().isEmpty()) {
            showAlert("提示", "该会话缺少工作目录信息");
            return;
        }
        String projectName = extractProjectNameFromPath(wd.trim());
        if (projectName == null || projectName.isEmpty()) {
            showAlert("提示", "无法识别项目名称");
            return;
        }
        try {
            boolean nowFav = favoriteProjectService.toggle(projectName);
            if (nowFav) {
                favoriteProjectNames.add(projectName);
                statusLabel.setText("已收藏项目: " + projectName);
            } else {
                favoriteProjectNames.remove(projectName);
                statusLabel.setText("已取消收藏项目: " + projectName);
            }
            buildPathTree(allSessions);
        } catch (SQLException e) {
            showError("收藏项目失败", e.getMessage());
        }
    }

    /**
     * 一键恢复会话：在项目目录打开 PowerShell 并自动执行 claude --resume
     */
    private void oneClickResume(Session session) {
        String workingDir = session.getWorkingDirectory();
        String sessionId = session.getOriginalUniqueId();
        if (workingDir == null || workingDir.trim().isEmpty()) {
            showAlert("错误", "该会话缺少工作目录信息，请刷新同步");
            return;
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            showAlert("错误", "该会话缺少会话 ID");
            return;
        }

        String escapedPath = workingDir.trim().replace("'", "''");
        String cmd = "Set-Location -LiteralPath '" + escapedPath + "'; claude --resume " + sessionId;
        System.out.println("[一键恢复] 目录: " + workingDir);
        System.out.println("[一键恢复] 命令: " + cmd);

        try {
            new ProcessBuilder(
                "cmd.exe", "/c", "start", "\"Claude\"",
                "powershell.exe", "-NoExit", "-NoLogo",
                "-Command", cmd
            ).start();
            // 记录恢复时间
            LocalDateTime now = LocalDateTime.now();
            session.setLastResumeTime(now);
            try { sessionService.updateLastResumeTime(session.getSessionId(), now); } catch (Exception ignored) {}
            // 刷新排序
            filterSessions();
            statusLabel.setText("已恢复: " + sessionId);
        } catch (Exception ex) {
            showAlert("错误", "启动失败: " + ex.getMessage());
        }
    }

    private void initSessionContextMenu() {
        // 一键恢复（置顶）
        MenuItem resumeItem = new MenuItem("🚀 一键恢复会话");
        resumeItem.setOnAction(e -> {
            Session sel = sessionList.getSelectionModel().getSelectedItem();
            if (sel != null) oneClickResume(sel);
        });

        MenuItem toggleFav = new MenuItem("⭐ 收藏/取消收藏");
        toggleFav.setOnAction(e -> {
            Session sel = sessionList.getSelectionModel().getSelectedItem();
            if (sel != null) toggleFavorite(sel);
        });

        MenuItem toggleFavProject = new MenuItem("📁 收藏此项目");
        toggleFavProject.setOnAction(e -> {
            Session sel = sessionList.getSelectionModel().getSelectedItem();
            if (sel != null) toggleFavoriteProject(sel);
        });

        MenuItem copyId = new MenuItem("复制恢复命令（claude --resume）");
        copyId.setOnAction(e -> {
            Session sel = sessionList.getSelectionModel().getSelectedItem();
            if (sel != null) {
                String cmd = "claude --resume " + sel.getOriginalUniqueId();
                ClipboardUtil.copyToClipboard(cmd);
                statusLabel.setText("已复制: " + cmd);
            }
        });

        MenuItem copyTitle = new MenuItem("复制会话标题");
        copyTitle.setOnAction(e -> {
            Session sel = sessionList.getSelectionModel().getSelectedItem();
            if (sel != null) {
                ClipboardUtil.copyToClipboard(sel.getTitle());
                statusLabel.setText("已复制标题");
            }
        });

        MenuItem openPowerShell = new MenuItem("在此路径打开 PowerShell");
        openPowerShell.setOnAction(e -> {
            Session sel = sessionList.getSelectionModel().getSelectedItem();
            if (sel == null) {
                showAlert("错误", "未选中会话");
                return;
            }
            String workingDir = sel.getWorkingDirectory();
            if (workingDir == null || workingDir.trim().isEmpty()) {
                showAlert("错误", "该会话没有工作目录信息（请刷新同步获取最新数据）");
                return;
            }
            workingDir = workingDir.trim();
            try {
                String escapedPath = workingDir.replace("'", "''");
                // PowerShell 命令
                String psCommand = "Set-Location -LiteralPath '" + escapedPath + "'";

                // 优先检测 Windows Terminal，其次传统 PowerShell
                String terminalExe = "powershell.exe";
                try {
                    new ProcessBuilder("where", "wt.exe").start().waitFor();
                    // wt.exe 存在，用 Windows Terminal 包装
                    if (Runtime.getRuntime().exec(new String[]{"where", "wt.exe"}).waitFor() == 0) {
                        // Windows Terminal 可用，但为了兼容直接用 PowerShell 新窗口
                    }
                } catch (Exception ignored) {}


                // 使用 cmd /c start 启动独立 PowerShell 窗口
                // 命令格式: cmd /c start "Sessionshelf" powershell.exe -NoExit -Command "..."
                String[] cmd = {
                    "cmd.exe", "/c", "start", "\"Sessionshelf\"",
                    "powershell.exe", "-NoExit", "-NoLogo",
                    "-Command", psCommand
                };
                System.out.println("[打开PS] 路径: " + workingDir);
                System.out.println("[打开PS] 命令: " + psCommand);

                Process proc = new ProcessBuilder(cmd).start();
                statusLabel.setText("已打开 PowerShell: " + workingDir);
            } catch (Exception ex) {
                String errMsg = "命令执行失败: " + ex.getMessage();
                System.err.println("[打开PS] " + errMsg);
                showAlert("错误", errMsg);
            }
        });

        MenuItem copyPath = new MenuItem("复制文件路径");
        copyPath.setOnAction(e -> {
            Session sel = sessionList.getSelectionModel().getSelectedItem();
            if (sel != null && sel.getSourceFilePath() != null) {
                ClipboardUtil.copyToClipboard(sel.getSourceFilePath());
                statusLabel.setText("已复制路径");
            }
        });

        MenuItem deleteSession = new MenuItem("删除会话");
        deleteSession.setOnAction(e -> {
            Session sel = sessionList.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "确定删除会话「" + (sel.getTitle() != null ? sel.getTitle() : "") + "」？");
            confirm.showAndWait().ifPresent(r -> {
                if (r == ButtonType.OK) {
                    try {
                        sessionService.deleteSession(sel.getSessionId());
                        allSessions.remove(sel);
                        sessions.remove(sel);
                        updateSessionCount();
                        statusLabel.setText("已删除会话");
                    } catch (SQLException ex) {
                        showError("删除失败", ex.getMessage());
                    }
                }
            });
        });

        ContextMenu menu = new ContextMenu(
            // 第一组：核心会话操作
            resumeItem, toggleFav, toggleFavProject,
            new SeparatorMenuItem(),
            // 第二组：终端 / 命令相关
            copyId, openPowerShell,
            new SeparatorMenuItem(),
            // 第三组：复制类
            copyTitle, copyPath,
            new SeparatorMenuItem(),
            // 第四组：危险操作
            deleteSession
        );
        sessionList.setContextMenu(menu);
    }

    // ============ 数据加载 ============

    private void loadData() {
        try {
            List<Session> rawSessions = sessionService.getAllSessions();
            // 过滤空会话：内容预览为空的会话不显示
            allSessions = new ArrayList<>();
            for (Session s : rawSessions) {
                if (s.getContentPreview() != null && !s.getContentPreview().trim().isEmpty()) {
                    allSessions.add(s);
                }
            }
            int filtered = rawSessions.size() - allSessions.size();
            if (filtered > 0) {
                statusLabel.setText("已过滤 " + filtered + " 条空会话");
            }
            try {
                favoriteSessionIds = favoriteService.getAllFavoriteIds();
            } catch (SQLException ignored) {}
            try {
                favoriteProjectNames = favoriteProjectService.getAll();
            } catch (SQLException ignored) {}
            sessions.clear();
            sessions.addAll(allSessions);
            buildPathTree(allSessions);
            updateSessionCount();
            // 后台构建内容索引
            buildContentCache(allSessions);
        } catch (SQLException e) {
            showError("加载会话失败", e.getMessage());
        }
    }

    /**
     * 根据选中的目录路径、来源、标签筛选会话列表
     */
    private void filterSessions() {
        List<Session> result = new ArrayList<>(allSessions);

        // 目录筛选：根据 workingDirectory 过滤
        if (selectedFolderPath != null) {
            if ("__UNCLASSIFIED__".equals(selectedFolderPath)) {
                // 未归档：workingDirectory 为空的会话
                result.removeIf(s -> s.getWorkingDirectory() != null && !s.getWorkingDirectory().trim().isEmpty());
            } else if ("__FAVORITE__".equals(selectedFolderPath)) {
                result.removeIf(s -> !favoriteSessionIds.contains(s.getSessionId()));
            } else if (selectedFolderPath != null && selectedFolderPath.startsWith("__PROJECT__:")) {
                String projectName = selectedFolderPath.substring("__PROJECT__:".length());
                result.removeIf(s -> {
                    String wd = s.getWorkingDirectory();
                    if (wd == null || wd.trim().isEmpty()) return true;
                    return !projectName.equals(extractProjectNameFromPath(wd.trim()));
                });
            } else {
                // 按 workingDirectory 前缀匹配
                String normalized = normalizePath(selectedFolderPath);
                result.removeIf(s -> {
                    String wd = s.getWorkingDirectory();
                    if (wd == null || wd.trim().isEmpty()) return true;
                    return !normalizePath(wd.trim()).startsWith(normalized);
                });
            }
        }

        // 来源筛选
        String src = sourceFilter.getValue();
        if (src != null && !"全部来源".equals(src)) {
            SourceType type = getSourceTypeByName(src);
            if (type != null) result.removeIf(s -> s.getSourceType() != type);
        }

        // 全文搜索（多关键词 AND 逻辑 + 内容全文匹配）
        if (currentSearchText != null && !currentSearchText.isEmpty()) {
            String[] keywords = currentSearchText.split("\\s+");
            result.removeIf(s -> !matchesAllKeywords(s, keywords));
        }

        // 排序
        String sort = sortOrder.getValue();
        if ("时间正序".equals(sort)) {
            result.sort((a, b) -> compareTime(a, b, false));
        } else if ("最近恢复".equals(sort)) {
            result.sort((a, b) -> {
                LocalDateTime ra = a.getLastResumeTime();
                LocalDateTime rb = b.getLastResumeTime();
                if (ra == null && rb == null) return compareTime(a, b, true); // 都没有恢复记录则按时按创建时间
                if (ra == null) return 1;
                if (rb == null) return -1;
                return rb.compareTo(ra);
            });
        } else {
            result.sort((a, b) -> compareTime(a, b, true));
        }

        sessions.clear();
        sessions.addAll(result);
        updateSessionCount();
    }

    /**
     * 检查会话是否匹配所有关键词（AND 逻辑）
     * 搜索范围：标题 + 内容预览 + 全文缓存
     */
    private boolean matchesAllKeywords(Session session, String[] keywords) {
        // 拼接搜索文本：标题 + 预览 + 全文缓存
        StringBuilder haystack = new StringBuilder();
        if (session.getTitle() != null) haystack.append(session.getTitle());
        haystack.append(" ");
        if (session.getContentPreview() != null) haystack.append(session.getContentPreview());
        haystack.append(" ");
        String cachedContent = contentCache.get(session.getSessionId());
        if (cachedContent != null) haystack.append(cachedContent);

        String text = haystack.toString();
        for (String keyword : keywords) {
            if (keyword.isEmpty()) continue;
            if (caseSensitive) {
                if (!text.contains(keyword)) return false;
            } else {
                if (!text.toLowerCase().contains(keyword.toLowerCase())) return false;
            }
        }
        return true;
    }

    private int compareTime(Session a, Session b, boolean desc) {
        if (a.getCreateTime() == null) return desc ? 1 : -1;
        if (b.getCreateTime() == null) return desc ? -1 : 1;
        return desc ? b.getCreateTime().compareTo(a.getCreateTime()) : a.getCreateTime().compareTo(b.getCreateTime());
    }

    private void updateSessionCount() {
        sessionCount.setText("共 " + sessions.size() + " 条");
    }

    private void updateDataSourceStatus() {
        String status = syncService.checkDataSourceStatus();
        dataSourceStatus.setText("数据源: " + status.replace("\n", " | "));
    }

    // ============ 会话选择 ============

    private void onSessionSelected(Session session) {
        selectedSession = session;
        if (session != null) displaySessionDetail(session);
    }

    // ============ WebView 渲染 ============

    private void displaySessionDetail(Session session) {
        sessionTitle.setText(session.getTitle() != null ? session.getTitle() : "未命名会话");
        sessionSource.setText("来源: " + (session.getSourceType() != null ? session.getSourceType().getDisplayName() : "未知"));
        sessionModel.setText("模型: " + (session.getModelName() != null ? session.getModelName() : "未知"));
        sessionTime.setText("时间: " + (session.getCreateTime() != null ? session.getCreateTime().toString() : "未知"));

        // 搜索导航栏：有搜索词时显示，否则隐藏
        boolean showNav = currentSearchText != null && !currentSearchText.isEmpty();
        searchNavBar.setVisible(showNav);
        searchNavBar.setManaged(showNav);
        if (showNav) matchCountLabel.setText("加载中...");

        contentWebView.getEngine().loadContent(buildLoadingHtml());

        new Thread(() -> {
            String content = syncService.readSessionContent(session);
            Platform.runLater(() -> renderChatHtml(content));
        }).start();
    }

    private void renderChatHtml(String content) {
        if (content == null || content.isEmpty()) {
            contentWebView.getEngine().loadContent(buildEmptyHtml());
            return;
        }

        // 先清洗控制字符和装饰符号
        content = sanitizeDisplayText(content);

        StringBuilder html = new StringBuilder();
        html.append(buildHtmlHeader());

        String[] lines = content.split("\n");
        String currentRole = null;
        StringBuilder currentMessage = new StringBuilder();

        for (String line : lines) {
            // 清洗行首特殊符号后再判断角色
            String cleaned = cleanRolePrefix(line);
            if (cleaned.startsWith("[用户]") || cleaned.startsWith("[user]")) {
                if (currentRole != null && currentMessage.length() > 0) {
                    appendBubble(html, currentRole, currentMessage.toString());
                }
                currentRole = "用户";
                currentMessage = new StringBuilder();
            } else if (cleaned.startsWith("[AI]") || cleaned.startsWith("[assistant]")) {
                if (currentRole != null && currentMessage.length() > 0) {
                    appendBubble(html, currentRole, currentMessage.toString());
                }
                currentRole = "AI";
                currentMessage = new StringBuilder();
            } else {
                if (currentMessage.length() > 0) currentMessage.append("\n");
                currentMessage.append(line);
            }
        }

        if (currentRole != null && currentMessage.length() > 0) {
            appendBubble(html, currentRole, currentMessage.toString());
        }

        html.append(buildHtmlFooter());
        contentWebView.getEngine().loadContent(html.toString());

        // 注入 Java 桥接（代码块复制）+ 匹配导航
        contentWebView.getEngine().getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == javafx.concurrent.Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) contentWebView.getEngine().executeScript("window");
                window.setMember("javaBridge", new Object() {
                    public void copyCode(String code) {
                        ClipboardUtil.copyToClipboard(code);
                    }
                });
                try { contentWebView.getEngine().executeScript("initCopyButtons()"); } catch (Exception ignored) {}
                if (currentSearchText != null && !currentSearchText.isEmpty()) {
                    contentWebView.getEngine().executeScript(buildMatchNavigationJs());
                    updateMatchCountLabel();
                }
            }
        });
    }

    private String buildHtmlHeader() {
        boolean dark = "dark".equals(currentTheme);
        String bg           = dark ? "#1e1e2e" : "#f0f2f5";
        String textColor    = dark ? "#cdd6f4" : "#333";
        String userBubbleBg = dark ? "#89b4fa" : "#667eea";
        String userText     = dark ? "#1e1e2e" : "#fff";
        String aiBubbleBg   = dark ? "#313244" : "#fff";
        String aiBorder     = dark ? "#45475a" : "#e0e0e0";
        String preBg        = dark ? "#11111b" : "#1e1e2e";
        String preBorder    = dark ? "#313244" : "#333";
        String preText      = "#cdd6f4";
        String shadow       = dark ? "0 2px 8px rgba(0,0,0,0.3)" : "0 2px 8px rgba(0,0,0,0.08)";

        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8">
            <style>
            *{box-sizing:border-box}
            body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','Microsoft YaHei',sans-serif;padding:20px 16px;background:%1$s;margin:0;line-height:1.7;color:%2$s}
            .msg{margin-bottom:20px;display:flex;flex-direction:column}
            .msg.user{align-items:flex-end}
            .msg.ai{align-items:flex-start}
            .bubble{max-width:80%%;padding:12px 16px;border-radius:14px;word-wrap:break-word;box-shadow:%6$s;font-size:14px;line-height:1.65}
            .user .bubble{background:%3$s;color:%4$s;border-bottom-right-radius:4px}
            .ai .bubble{background:%5$s;color:%2$s;border-bottom-left-radius:4px;border:1px solid %9$s}
            .role{font-size:11px;margin-bottom:4px;font-weight:600;letter-spacing:.3px}
            .user .role{color:rgba(137,180,250,0.9)}
            .ai .role{color:#6c7086}
            .user .role:after{content:' 用户 ↑'}
            .ai .role:after{content:' AI ↓'}
            /* 代码块 */
            pre{position:relative;background:%7$s;color:%8$s;padding:16px;border-radius:10px;overflow:auto;max-height:500px;font-size:13px;line-height:1.55;margin:10px 0;font-family:'JetBrains Mono','Cascadia Code','Fira Code',Consolas,monospace;border:1px solid %10$s;tab-size:4;white-space:pre;word-wrap:normal}
            pre code{font-family:'JetBrains Mono','Cascadia Code','Fira Code',Consolas,monospace;font-size:13px;white-space:pre}
            .ic{background:rgba(0,0,0,0.08);padding:2px 6px;border-radius:4px;font-size:0.92em;font-family:'JetBrains Mono','Cascadia Code','Fira Code',Consolas,monospace}
            .user .ic{background:rgba(255,255,255,0.2)}
            /* 高亮 */
            mark{background:#f9e2af;color:#1e1e2e;padding:1px 2px;border-radius:2px}
            mark.active{background:#fab387;color:#1e1e2e}
            /* 语法高亮 */
            .kw{color:#cba6f7} .str{color:#a6e3a1} .cm{color:#6c7086;font-style:italic}
            .num{color:#fab387} .fn{color:#89b4fa} .op{color:#89dceb}
            </style></head><body>
            """.formatted(bg, textColor, userBubbleBg, userText, aiBubbleBg, shadow,
                          preBg, preText, aiBorder, preBorder);
    }

    private String buildHtmlFooter() {
        return """
            <style>
            pre{position:relative}
            .copy-btn{position:absolute;top:8px;right:8px;padding:5px 12px;font-size:11px;
              border:none;border-radius:4px;cursor:pointer;opacity:.6;transition:all .2s;
              font-family:sans-serif;z-index:100;background:#45475a;color:#cdd6f4;
              box-shadow:0 1px 3px rgba(0,0,0,.3)}
            .copy-btn:hover{opacity:1;background:#585b70;box-shadow:0 2px 8px rgba(137,180,250,.4)}
            .copy-toast{position:fixed;bottom:30px;left:50%;transform:translateX(-50%);
              padding:6px 18px;border-radius:6px;font-size:12px;color:#fff;
              background:rgba(0,0,0,.7);z-index:9999;pointer-events:none;
              animation:toastIn .3s ease,toastOut .3s ease 1.2s}
            @keyframes toastIn{from{opacity:0;transform:translateX(-50%) translateY(10px)}
              to{opacity:1;transform:translateX(-50%) translateY(0)}}
            @keyframes toastOut{from{opacity:1}to{opacity:0}}
            </style>
            <script>
            function initCopyButtons(){
              document.querySelectorAll('pre').forEach(function(pre){
                if(pre.querySelector('.copy-btn')) return;
                var btn=document.createElement('button');
                btn.className='copy-btn';
                btn.textContent='复制';
                btn.onclick=function(){
                  var code=pre.querySelector('code');
                  var text=code?code.textContent:pre.textContent;
                  try{window.javaBridge.copyCode(text)}catch(e){}
                  var toast=document.createElement('div');
                  toast.className='copy-toast';
                  toast.textContent='✔ 已复制';
                  document.body.appendChild(toast);
                  setTimeout(function(){toast.remove()},1500);
                };
                pre.appendChild(btn);
              });
              highlightAllCode();
            }
            function highlightAllCode(){
              document.querySelectorAll('pre code').forEach(function(code){
                if(code.dataset.highlighted) return;
                code.dataset.highlighted='1';
                var text=code.textContent;
                var lang=code.className.replace('language-','');
                // 简单的语法高亮：关键字、字符串、注释、数字
                var result='';
                var i=0;
                while(i<text.length){
                  // 单行注释 //
                  if(text.substr(i,2)=='//'){
                    var end=text.indexOf('\\n',i);
                    if(end==-1) end=text.length;
                    result+='<span class="cm">'+escapeH(text.substring(i,end))+'</span>';
                    i=end; continue;
                  }
                  // 多行注释 /* */
                  if(text.substr(i,2)=='/*'){
                    var end=text.indexOf('*/',i);
                    if(end==-1) end=text.length; else end+=2;
                    result+='<span class="cm">'+escapeH(text.substring(i,end))+'</span>';
                    i=end; continue;
                  }
                  // 字符串
                  if(text[i]=='\''||text[i]=='"'||text[i]=='`'){
                    var q=text[i],j=i+1;
                    while(j<text.length&&text[j]!=q){if(text[j]=='\\\\')j++;j++;}
                    if(j<text.length) j++;
                    result+='<span class="str">'+escapeH(text.substring(i,j))+'</span>';
                    i=j; continue;
                  }
                  // 数字
                  if(/[0-9]/.test(text[i])&&(i==0||!/[a-zA-Z_]/.test(text[i-1]))){
                    var j=i;
                    while(j<text.length&&/[0-9a-fA-FxX._]/.test(text[j])) j++;
                    result+='<span class="num">'+escapeH(text.substring(i,j))+'</span>';
                    i=j; continue;
                  }
                  // 关键字
                  var kwMatch=null;
                  var kws=['public','private','class','void','int','String','if','else','for','while','return','new','static','final','import','package','try','catch','throw','throws','extends','implements','interface','abstract','boolean','char','double','float','long','short','byte','null','true','false','this','super','var','let','const','function','async','await','def','from','import','in','is','not','and','or','None','True','False','with','as','yield','lambda'];
                  for(var k=0;k<kws.length;k++){
                    var kw=kws[k];
                    if(text.substr(i,kw.length)==kw&&(i+kw.length>=text.length||!/[a-zA-Z0-9_]/.test(text[i+kw.length]))&&(i==0||!/[a-zA-Z0-9_]/.test(text[i-1]))){
                      kwMatch=kw; break;
                    }
                  }
                  if(kwMatch){
                    result+='<span class="kw">'+escapeH(kwMatch)+'</span>';
                    i+=kwMatch.length; continue;
                  }
                  result+=escapeH(text[i]); i++;
                }
                code.innerHTML=result;
              });
            }
            function escapeH(s){return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}
            </script>
            </body></html>
            """;
    }

    private String buildLoadingHtml() {
        return "<html><body style='display:flex;justify-content:center;align-items:center;height:100vh;font-family:sans-serif;color:#999'><div style='text-align:center'><div style='font-size:20px'>⏳</div><div>加载中...</div></div></body></html>";
    }

    private String buildEmptyHtml() {
        return "<html><body style='display:flex;justify-content:center;align-items:center;height:100vh;font-family:sans-serif;color:#999'><div style='text-align:center'><div style='font-size:40px'>💬</div><div>暂无对话内容</div></div></body></html>";
    }

    private void appendBubble(StringBuilder html, String role, String message) {
        String cls = "用户".equals(role) ? "user" : "ai";
        String label = "用户".equals(role) ? "[用户]" : "[AI]";
        html.append("<div class='msg ").append(cls).append("'>");
        html.append("<div class='role'>").append(label).append("</div>");
        html.append("<div class='bubble'><div class='content'>").append(formatContent(message)).append("</div></div></div>");
    }

    private String formatContent(String message) {
        if (message == null || message.isEmpty()) return "";
        String r = escapeHtml(message);

        // 代码块
        Pattern p = Pattern.compile("```(\\w*)\\n([\\s\\S]*?)```");
        Matcher m = p.matcher(r);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String lang = m.group(1);
            String code = m.group(2);
            m.appendReplacement(sb, Matcher.quoteReplacement("<pre><code class='language-" + lang + "'>" + code + "</code></pre>"));
        }
        m.appendTail(sb);
        r = sb.toString();

        r = r.replaceAll("```([\\s\\S]*?)```", "<pre><code>$1</code></pre>");
        r = r.replaceAll("`([^`]+)`", "<span class='ic'>$1</span>");
        r = r.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
        r = r.replaceAll("\\*([^*]+)\\*", "<em>$1</em>");
        r = r.replaceAll("(?!</pre>)\\n(?!<)", "<br>");
        // 最后一步：高亮搜索关键词
        r = highlightKeywordsInHtml(r);
        return r;
    }

    private String escapeHtml(String t) {
        if (t == null) return "";
        return t.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }

    /**
     * 清洗显示文本：白名单策略，只保留正常可读字符
     */
    private String sanitizeDisplayText(String text) {
        if (text == null) return "";
        String cleaned = text.replaceAll(
            "[^\\x20-\\x7E\\n\\r\\t\\u4e00-\\u9fff\\u3000-\\u303f\\uff01-\\uff5e\\uff65-\\uffef]", "");
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
        return cleaned;
    }

    /**
     * 清洗角色行前缀：找到第一个 [ 或中文字，截取之后的内容
     * 例如 "??? [用户] hello" → "[用户] hello"
     */
    private String cleanRolePrefix(String line) {
        if (line == null) return "";
        // 找到第一个 [ 或中文字符的位置
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '[' || (c >= '一' && c <= '鿿')) {
                return line.substring(i);
            }
        }
        return line.trim();
    }

    /**
     * 对 HTML 文本中的搜索关键词添加 <mark> 高亮标签
     */
    private String highlightKeywordsInHtml(String html) {
        if (currentSearchText == null || currentSearchText.isEmpty() || html == null) return html;
        String[] keywords = currentSearchText.split("\\s+");
        String result = html;
        for (String keyword : keywords) {
            if (keyword.isEmpty()) continue;
            String escaped = escapeHtml(keyword);
            // 使用 (?i) 实现大小写不敏感高亮，匹配时保留原文大小写
            String regex = caseSensitive ? Pattern.quote(escaped) : "(?i)" + Pattern.quote(escaped);
            result = result.replaceAll(regex, "<mark>$0</mark>");
        }
        return result;
    }

    /**
     * 构建 JS 代码：初始化匹配导航，滚动到第一个 <mark>
     */
    private String buildMatchNavigationJs() {
        return """
            (function() {
                window._marks = document.querySelectorAll('mark');
                window._markIdx = -1;
                if (window._marks.length > 0) {
                    window._markIdx = 0;
                    window._marks[0].classList.add('active');
                    window._marks[0].scrollIntoView({behavior:'smooth', block:'center'});
                }
            })();
            """;
    }

    /**
     * 更新匹配计数标签
     */
    private void updateMatchCountLabel() {
        try {
            int total = (int) contentWebView.getEngine().executeScript("window._marks ? window._marks.length : 0");
            int current = (int) contentWebView.getEngine().executeScript("window._markIdx >= 0 ? window._markIdx + 1 : 0");
            if (total > 0) {
                matchCountLabel.setText(current + " / " + total);
                searchNavBar.setVisible(true);
                searchNavBar.setManaged(true);
            } else {
                matchCountLabel.setText("无匹配");
                searchNavBar.setVisible(currentSearchText != null && !currentSearchText.isEmpty());
                searchNavBar.setManaged(currentSearchText != null && !currentSearchText.isEmpty());
            }
        } catch (Exception e) {
            matchCountLabel.setText("");
        }
    }

    @FXML
    private void onPrevMatch() {
        try {
            contentWebView.getEngine().executeScript("""
                (function() {
                    if (!window._marks || window._marks.length === 0) return;
                    window._marks[window._markIdx].classList.remove('active');
                    window._markIdx = (window._markIdx - 1 + window._marks.length) % window._marks.length;
                    window._marks[window._markIdx].classList.add('active');
                    window._marks[window._markIdx].scrollIntoView({behavior:'smooth', block:'center'});
                })();
                """);
            updateMatchCountLabel();
        } catch (Exception ignored) {}
    }

    @FXML
    private void onNextMatch() {
        try {
            contentWebView.getEngine().executeScript("""
                (function() {
                    if (!window._marks || window._marks.length === 0) return;
                    window._marks[window._markIdx].classList.remove('active');
                    window._markIdx = (window._markIdx + 1) % window._marks.length;
                    window._marks[window._markIdx].classList.add('active');
                    window._marks[window._markIdx].scrollIntoView({behavior:'smooth', block:'center'});
                })();
                """);
            updateMatchCountLabel();
        } catch (Exception ignored) {}
    }

    // ============ 菜单操作 ============

    @FXML
    private void onRefreshSync() {
        statusLabel.setText("正在同步...");
        syncService.setScanPaths(scanPaths);
        new Thread(() -> {
            int count = syncService.syncAll();
            Platform.runLater(() -> {
                loadData();
                updateDataSourceStatus();
                statusLabel.setText("同步完成，共 " + count + " 条");
            });
        }).start();
    }

    @FXML
    private void onPathSettings() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("扫描路径设置");
        dialog.setHeaderText("配置会话文件扫描路径\n每行一个路径，启动时自动扫描这些路径下的 .claude/projects/ 目录");

        TextArea textArea = new TextArea();
        textArea.setPromptText("每行一个路径，例如:\nC:\\Users\\YourName\nD:\\SoftwareCoding\\workspace");
        textArea.setPrefSize(450, 200);

        StringBuilder sb = new StringBuilder();
        for (String p : scanPaths) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(p);
        }
        textArea.setText(sb.toString());

        dialog.getDialogPane().setContent(textArea);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                String text = textArea.getText();
                String[] lines = text.split("\\n");
                scanPaths.clear();
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && new File(trimmed).exists()) {
                        scanPaths.add(trimmed);
                    } else if (!trimmed.isEmpty()) {
                        showAlert("警告", "路径不存在，已跳过: " + trimmed);
                    }
                }
                if (scanPaths.isEmpty()) {
                    scanPaths.add(System.getProperty("user.home"));
                }
                saveScanPaths();
                onRefreshSync();
            }
        });
    }

    @FXML
    private void onExit() {
        saveLayoutPositions();
        Platform.exit();
    }

    @FXML
    private void onSearch() {
        doSearch(searchField.getText());
        if (!currentSearchText.isEmpty()) {
            statusLabel.setText("搜索: " + currentSearchText + " → " + sessions.size() + " 条匹配");
        }
    }

    @FXML
    private void onAbout() {
        showAlert("关于", "Sessionshelf v1.0\n\n本地离线多源 AI 会话统一管理工具");
    }

    // ============ 主题切换 ============

    @FXML
    private void onLightTheme() {
        currentTheme = "light";
        config.setString(THEME_KEY, "light");
        config.save();
        applyTheme();
    }

    @FXML
    private void onDarkTheme() {
        currentTheme = "dark";
        config.setString(THEME_KEY, "dark");
        config.save();
        applyTheme();
    }

    /**
     * 应用当前主题：替换 Scene 的 CSS 样式表，WebView 内容同步切换
     */
    private void applyTheme() {
        try {
            javafx.scene.Scene scene = rootPane.getScene();
            if (scene == null) return;

            scene.getStylesheets().clear();
            String cssPath = "dark".equals(currentTheme) ? DARK_CSS : LIGHT_CSS;
            scene.getStylesheets().add(getClass().getResource(cssPath).toExternalForm());

            // WebView 的 HTML 样式通过 buildHtmlHeader 动态生成，无需额外处理
            // 如果当前有选中的会话，刷新 WebView 以应用新主题
            if (selectedSession != null) {
                displaySessionDetail(selectedSession);
            }
        } catch (Exception e) {
            System.err.println("主题切换失败: " + e.getMessage());
        }
    }

    // ============ 会话操作 ============

    @FXML
    private void onCopyAll() {
        if (selectedSession == null) { showAlert("提示", "请先选择会话"); return; }
        ClipboardUtil.copyToClipboard(syncService.readSessionContent(selectedSession));
        statusLabel.setText("已复制");
    }

    @FXML
    private void onExportMarkdown() {
        if (selectedSession == null) { showAlert("提示", "请先选择会话"); return; }
        DirectoryChooser c = new DirectoryChooser();
        c.setTitle("选择导出目录");
        File dir = c.showDialog(contentWebView.getScene().getWindow());
        if (dir != null) {
            try {
                File f = exportService.exportToMarkdown(selectedSession, dir.getAbsolutePath());
                statusLabel.setText("导出: " + f.getName());
            } catch (Exception e) { showError("导出失败", e.getMessage()); }
        }
    }

    @FXML
    private void onExportJson() {
        if (selectedSession == null) { showAlert("提示", "请先选择会话"); return; }
        DirectoryChooser c = new DirectoryChooser();
        c.setTitle("选择导出目录");
        File dir = c.showDialog(contentWebView.getScene().getWindow());
        if (dir != null) {
            try {
                File f = exportService.exportToJson(selectedSession, dir.getAbsolutePath());
                statusLabel.setText("导出: " + f.getName());
            } catch (Exception e) { showError("导出失败", e.getMessage()); }
        }
    }

    // ============ 工具方法 ============

    public List<String> getScanPaths() { return scanPaths; }

    private String normalizePath(String path) {
        if (path == null) return "";
        // 先统一为系统分隔符，再把连续分隔符压成单个（修复双反斜杠问题）
        return path.replace("/", File.separator)
                    .replace("\\", File.separator)
                    .replace(File.separator + File.separator, File.separator);
    }

    private String extractDrive(String path) {
        if (path == null || path.isEmpty()) return null;
        if (path.length() >= 2 && path.charAt(1) == ':') return path.substring(0, 2).toUpperCase();
        if (path.startsWith("/")) return "/";
        return null;
    }

    private SourceType getSourceTypeByName(String name) {
        if (name == null) return null;
        switch (name) {
            case "Claude Code": return SourceType.CLAUDE_CODE;
            case "OpenAI Codex": return SourceType.OPENAI_CODEX;
            case "CC Switch": return SourceType.CC_SWITCH;
            default: return null;
        }
    }

    private void showAlert(String title, String content) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setContentText(content);
        a.showAndWait();
    }

    private void showError(String title, String content) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setContentText(content);
        a.showAndWait();
    }

    // ============ 单元格 ============

    /**
     * 构建关键词高亮的 TextFlow
     * 将文本按搜索关键词拆分，匹配部分用黄色背景标注
     */
    private TextFlow buildHighlightedText(String text, String baseStyle) {
        TextFlow flow = new TextFlow();
        if (text == null || text.isEmpty()) return flow;

        String[] keywords = currentSearchText.split("\\s+");
        // 构建正则：所有关键词用 | 连接
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < keywords.length; i++) {
            if (keywords[i].isEmpty()) continue;
            if (pattern.length() > 0) pattern.append("|");
            pattern.append(Pattern.quote(keywords[i]));
        }
        if (pattern.length() == 0) {
            Text t = new Text(text);
            t.setStyle(baseStyle);
            flow.getChildren().add(t);
            return flow;
        }

        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        try {
            Pattern p = Pattern.compile(pattern.toString(), flags);
            Matcher m = p.matcher(text);
            int last = 0;
            while (m.find()) {
                // 匹配前的普通文本
                if (m.start() > last) {
                    Text before = new Text(text.substring(last, m.start()));
                    before.setStyle(baseStyle);
                    flow.getChildren().add(before);
                }
                // 匹配的关键词（高亮）
                Text match = new Text(m.group());
                match.setStyle(baseStyle + "-fx-fill:#333;-rtfx-background-color:#fff176;");
                flow.getChildren().add(match);
                last = m.end();
            }
            // 剩余文本
            if (last < text.length()) {
                Text after = new Text(text.substring(last));
                after.setStyle(baseStyle);
                flow.getChildren().add(after);
            }
        } catch (Exception e) {
            // 正则出错时降级为普通文本
            Text t = new Text(text);
            t.setStyle(baseStyle);
            flow.getChildren().add(t);
        }
        return flow;
    }

    private class SessionListCell extends ListCell<Session> {
        @Override
        protected void updateItem(Session session, boolean empty) {
            super.updateItem(session, empty);
            if (empty || session == null) {
                setText(null);
                setGraphic(null);
                setTooltip(null);
            } else {
                VBox vbox = new VBox(3);
                vbox.setPadding(new javafx.geometry.Insets(2, 0, 2, 0));

                // 标题：只显示项目名（去掉 - sessionId 后缀）
                String rawTitle = session.getTitle() != null ? session.getTitle() : "未命名会话";
                String displayTitle = extractProjectTitle(rawTitle);
                Label title = new Label(displayTitle);
                title.setStyle("-fx-font-weight:bold;-fx-font-size:13px;");
                title.setWrapText(false);
                title.setMaxWidth(320);
                title.setTooltip(new Tooltip(rawTitle));
                vbox.getChildren().add(title);

                // 信息行：来源 + 模型 + 时间 + 星标
                HBox info = new HBox(6);
                info.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                String src = session.getSourceType() != null ? session.getSourceType().getDisplayName() : "";
                Label srcLabel = new Label(src);
                srcLabel.setStyle("-fx-font-size:10px;-fx-text-fill:#666;-fx-background-color:#e3f2fd;-fx-padding:1 5;-fx-background-radius:6;");
                info.getChildren().add(srcLabel);
                // 模型
                if (session.getModelName() != null && !session.getModelName().isEmpty()) {
                    Label modelLabel = new Label(session.getModelName());
                    modelLabel.setStyle("-fx-font-size:10px;-fx-text-fill:#888;");
                    info.getChildren().add(modelLabel);
                }
                Label time = new Label(session.getCreateTime() != null ? session.getCreateTime().toLocalDate().toString() : "");
                time.setStyle("-fx-font-size:10px;-fx-text-fill:#999;");
                info.getChildren().add(time);
                // 收藏星标
                if (favoriteSessionIds.contains(session.getSessionId())) {
                    Label star = new Label("⭐");
                    star.setStyle("-fx-font-size:11px;");
                    info.getChildren().add(star);
                }
                vbox.getChildren().add(info);

                // 内容预览（截断到80字）
                String preview = session.getContentPreview();
                if (preview != null && !preview.isEmpty()) {
                    String shortPreview = preview.length() > 80 ? preview.substring(0, 80) + "…" : preview;
                    Label previewLabel = new Label(shortPreview);
                    previewLabel.setStyle("-fx-font-size:11px;-fx-text-fill:#888;");
                    previewLabel.setWrapText(false);
                    previewLabel.setMaxWidth(380);
                    vbox.getChildren().add(previewLabel);
                }

                setGraphic(vbox);
            }
        }

        /** 从原始标题提取项目名：去掉 " - uuid.jsonl" 后缀 */
        private String extractProjectTitle(String raw) {
            if (raw == null) return "未命名";
            int lastDash = raw.lastIndexOf(" - ");
            if (lastDash > 0) {
                String candidate = raw.substring(lastDash + 3);
                // 如果是 UUID 格式（含 - 且长度 > 20），取前面的项目名
                if (candidate.matches("[a-f0-9\\-]{20,}")) {
                    return raw.substring(0, lastDash);
                }
            }
            return raw;
        }
    }

}
