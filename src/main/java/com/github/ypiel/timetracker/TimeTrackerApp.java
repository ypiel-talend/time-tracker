package com.github.ypiel.timetracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimeTrackerApp extends JFrame {
    private JTable ticketTable;
    private JTable todoTable;
    private JButton pauseButton;
    private JLabel dayDurationLabel;
    private MyTicketTableModel ticketTableModel;
    private MyTodoTableModel todoTableModel;
    private boolean isPaused = false;
    private Timer dayTimer;
    private long dayStartTime;
    private long dayElapsedTime = 0;
    private LocalDate currentDate;
    private JCheckBox hideDoneCheckBox;
    private JCheckBox hideTicketDoneCheckBox;

    // Pour la sauvegarde périodique
    private Timer saveTimer;
    private static final String OUTPUT_FILE = "time-tracker";
    private static final String EXT = ".json";
    private static final String OUTPUT_DIR = System.getProperty("time-tracker.output_dir",System.getProperty("user.home") + File.separator + "TimeTracker");

    // Variable pour stocker le ticket précédemment sélectionné
    private Ticket previousSelectedTicket = null;

    // Icone de l'œil pour le lien
    private static ImageIcon eyeIcon;

    // Modèles de tables pour les durées quotidiennes du ticket et du todo sélectionnés
    private TicketDateDurationTableModel ticketDateDurationTableModel;
    private TodoDateDurationTableModel todoDateDurationTableModel;

    public TimeTrackerApp() {
        super("Time Tracker");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 800);
        setLocationRelativeTo(null);

        // Charger l'icône de l'œil
        eyeIcon = new ImageIcon(getClass().getResource("/icons/eye.png"));

        // Initialiser les composants
        ticketTableModel = new MyTicketTableModel();
        ticketTable = new JTable(ticketTableModel);

        // Définir l'éditeur personnalisé pour la colonne de statut des tickets
        JComboBox<TicketStatus> ticketStatusComboBox = new JComboBox<>(TicketStatus.values());
        ticketTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(ticketStatusComboBox));

        // Configurer le renderer pour la colonne "Lien"
        ticketTable.getColumnModel().getColumn(4).setCellRenderer(new LinkCellRenderer());

        // Ajouter un ListSelectionListener au tableau des tickets
        ticketTable.getSelectionModel().addListSelectionListener(new TicketSelectionHandler());

        // Configurer le comportement d'édition du tableau des tickets
        ticketTable.setSurrendersFocusOnKeystroke(true);

        // Ajouter un MouseListener pour détecter le clic sur l'icône d'œil
        ticketTable.addMouseListener(new TicketMouseAdapter());

        JScrollPane ticketScrollPane = new JScrollPane(ticketTable);

        hideTicketDoneCheckBox = new JCheckBox("Cacher les tickets terminés");
        hideTicketDoneCheckBox.addActionListener(e -> ticketTableModel.setHideDone(hideTicketDoneCheckBox.isSelected()));

        JPanel ticketTopPanel = new JPanel(new BorderLayout());
        ticketTopPanel.add(hideTicketDoneCheckBox, BorderLayout.WEST);

        // Panneau pour les durées quotidiennes du ticket sélectionné
        ticketDateDurationTableModel = new TicketDateDurationTableModel();
        JTable ticketDateDurationTable = new JTable(ticketDateDurationTableModel);
        JScrollPane ticketDateDurationScrollPane = new JScrollPane(ticketDateDurationTable);

        // Split pane vertical pour le panneau des tickets
        JSplitPane ticketSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        ticketSplitPane.setTopComponent(ticketScrollPane);
        ticketSplitPane.setBottomComponent(ticketDateDurationScrollPane);
        ticketSplitPane.setDividerLocation(300);

        JPanel ticketPanel = new JPanel(new BorderLayout());
        ticketPanel.add(ticketTopPanel, BorderLayout.NORTH);
        ticketPanel.add(ticketSplitPane, BorderLayout.CENTER);

        // Panneau de droite pour la liste des todos
        todoTableModel = new MyTodoTableModel();
        todoTable = new JTable(todoTableModel);
        todoTable.setSurrendersFocusOnKeystroke(true);

        // Définir l'éditeur personnalisé pour la colonne de statut des todos
        JComboBox<TodoStatus> todoStatusComboBox = new JComboBox<>(TodoStatus.values());
        todoTable.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(todoStatusComboBox));

        JScrollPane todoScrollPane = new JScrollPane(todoTable);

        hideDoneCheckBox = new JCheckBox("Cacher les actions terminées");
        hideDoneCheckBox.addActionListener(e -> todoTableModel.setHideDone(hideDoneCheckBox.isSelected()));

        JPanel todoTopPanel = new JPanel(new BorderLayout());
        todoTopPanel.add(hideDoneCheckBox, BorderLayout.WEST);

        // Panneau pour les durées quotidiennes du todo sélectionné
        todoDateDurationTableModel = new TodoDateDurationTableModel();
        JTable todoDateDurationTable = new JTable(todoDateDurationTableModel);
        JScrollPane todoDateDurationScrollPane = new JScrollPane(todoDateDurationTable);

        // Split pane vertical pour le panneau des todos
        JSplitPane todoSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        todoSplitPane.setTopComponent(todoScrollPane);
        todoSplitPane.setBottomComponent(todoDateDurationScrollPane);
        todoSplitPane.setDividerLocation(300);

        JPanel todoPanel = new JPanel(new BorderLayout());
        todoPanel.add(todoTopPanel, BorderLayout.NORTH);
        todoPanel.add(todoSplitPane, BorderLayout.CENTER);

        // Ajouter un ListSelectionListener au tableau des todos
        todoTable.getSelectionModel().addListSelectionListener(new TodoSelectionHandler());

        // Split pane pour contenir les deux tableaux
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, ticketPanel, todoPanel);
        splitPane.setDividerLocation(500);

        pauseButton = new JButton("Pause");
        pauseButton.setBackground(Color.GREEN.darker());
        pauseButton.setForeground(Color.WHITE);
        pauseButton.setFont(new Font("Arial", Font.BOLD, 24));
        pauseButton.addActionListener(e -> togglePause());

        dayDurationLabel = new JLabel("Durée de travail aujourd'hui : 00:00:00");
        dayDurationLabel.setFont(new Font("Arial", Font.PLAIN, 18));

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BorderLayout());
        bottomPanel.add(pauseButton, BorderLayout.CENTER);
        bottomPanel.add(dayDurationLabel, BorderLayout.EAST);

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BorderLayout());
        JButton removeTicketButton = new JButton("Remove Ticket");
        removeTicketButton.addActionListener(e -> removeSelectedTicket());
        JButton removeTodoButton = new JButton("Remove Todo");
        removeTodoButton.addActionListener(e -> removeSelectedTodo());
        topPanel.add(removeTicketButton, BorderLayout.WEST);
        topPanel.add(removeTodoButton, BorderLayout.EAST);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(topPanel, BorderLayout.NORTH);
        getContentPane().add(splitPane, BorderLayout.CENTER);
        getContentPane().add(bottomPanel, BorderLayout.SOUTH);

        // Initialiser les timers et charger l'état
        currentDate = LocalDate.now();
        dayStartTime = System.currentTimeMillis();

        // Charger l'état depuis le fichier
        loadState();

        startDayTimer();
        startSaveTimer();

        // Ajouter un écouteur de fenêtre pour sauvegarder l'état à la fermeture
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveState();
                super.windowClosing(e);
            }
        });

        ImageIcon appIcon = new ImageIcon(getClass().getResource("/icons/clock.png"));
        setIconImage(appIcon.getImage());

        setVisible(true);
    }

    private void error(String message, Exception e) {
        log.error(message, e);
        JOptionPane.showMessageDialog(this, message+"\n"+e.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        System.exit(1);
    }

    private void removeSelectedTicket() {
        int selectedRow = ticketTable.getSelectedRow();
        if (selectedRow >= 0) {
            ticketTableModel.removeTicket(selectedRow);
        }
    }

    private void removeSelectedTodo() {
        int selectedRow = todoTable.getSelectedRow();
        if (selectedRow >= 0) {
            todoTableModel.removeTodo(selectedRow);
        }
    }

    private void togglePause() {
        isPaused = !isPaused;
        if (isPaused) {
            pauseButton.setText("Reprendre");
            // Arrêter les timers
            ticketTableModel.pauseAll();
            todoTableModel.pauseAll();
            dayElapsedTime += System.currentTimeMillis() - dayStartTime;
            dayTimer.stop();
        } else {
            pauseButton.setText("Pause");
            // Vérifier si la date a changé
            if (!currentDate.equals(LocalDate.now())) {
                currentDate = LocalDate.now();
                dayElapsedTime = 0;
            }
            dayStartTime = System.currentTimeMillis();
            startDayTimer();
            // Reprendre les timers
            ticketTableModel.resumeSelected();
            todoTableModel.resumeSelected();
        }
    }

    private void startDayTimer() {
        dayTimer = new Timer(1000, e -> updateDayDuration());
        dayTimer.start();
    }

    private void updateDayDuration() {
        long elapsed = dayElapsedTime + (System.currentTimeMillis() - dayStartTime);
        dayDurationLabel.setText("Durée de travail aujourd'hui : " + formatDuration(elapsed));
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long hh = seconds / 3600;
        long mm = (seconds % 3600) / 60;
        long ss = seconds % 60;
        return String.format("%02d:%02d:%02d", hh, mm, ss);
    }

    private void startSaveTimer() {
        saveTimer = new Timer(60000, e -> saveState());
        saveTimer.start();
    }

    private void saveState() {
        String outputFilePath = getOutputFilePath();
        try (Writer writer = new FileWriter(outputFilePath+EXT)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            AppState appState = new AppState();
            appState.dayElapsedTime = dayElapsedTime;
            if (!isPaused) {
                appState.dayElapsedTime += System.currentTimeMillis() - dayStartTime;
            }
            appState.currentDate = currentDate.toString();
            appState.tickets = new ArrayList<>();
            for (Ticket ticket : ticketTableModel.tickets) {
                TicketState ts = new TicketState();
                ts.jiraId = ticket.jiraId;
                ts.url = ticket.url;
                ts.status = ticket.status.name();
                ts.comment = ticket.comment;
                ts.elapsedTime = ticket.elapsedTime;
                if (!isPaused && ticket.timer.isRunning()) {
                    ts.elapsedTime += System.currentTimeMillis() - ticket.startTime;
                }
                // Sauvegarder dailyTimeSpent
                ts.dailyTimeSpent = new HashMap<>();
                for (Map.Entry<LocalDate, Long> entry : ticket.dailyTimeSpent.entrySet()) {
                    ts.dailyTimeSpent.put(entry.getKey().toString(), entry.getValue());
                }
                // Sauvegarder la liste des todos
                ts.todos = new ArrayList<>();
                for (TodoItem todo : ticket.todoList) {
                    TodoState todoState = new TodoState();
                    todoState.status = todo.status.name();
                    todoState.title = todo.title;
                    todoState.comment = todo.comment;
                    todoState.elapsedTime = todo.elapsedTime;
                    if (!isPaused && todo.timer != null && todo.timer.isRunning()) {
                        todoState.elapsedTime += System.currentTimeMillis() - todo.startTime;
                    }
                    // Sauvegarder dailyTimeSpent
                    todoState.dailyTimeSpent = new HashMap<>();
                    for (Map.Entry<LocalDate, Long> entry : todo.dailyTimeSpent.entrySet()) {
                        todoState.dailyTimeSpent.put(entry.getKey().toString(), entry.getValue());
                    }
                    ts.todos.add(todoState);
                }
                appState.tickets.add(ts);
            }
            // Sauvegarder l'index du ticket sélectionné
            appState.selectedTicketIndex = ticketTable.getSelectedRow();
            appState.selectedTodoIndex = todoTable.getSelectedRow();
            gson.toJson(appState, writer);
            System.out.println("État de l'application sauvegardé en JSON.");

            // Do a daily backup
            Files.copy(Paths.get(outputFilePath+EXT), Paths.get(outputFilePath+"_"+getYearAndDay()+EXT), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static String getYearAndDay() {
        LocalDate date = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-DDD");
        return date.format(formatter);
    }

    private String getOutputFilePath() {
        // Créer le répertoire de sortie s'il n'existe pas
        log.debug("Time-tracker output directory: {}", OUTPUT_DIR);
        Path outputDir = Paths.get(OUTPUT_DIR);
        if (Files.notExists(outputDir)) {
            try {
                Files.createDirectories(outputDir);
            } catch (IOException e) {
                error("Erreur lors de la création du répertoire de sortie.", e);
            }
        }

        String outputFilePath = OUTPUT_DIR + File.separator + OUTPUT_FILE;
        log.info("Time-tracker output file: {}", outputFilePath+EXT);
        return outputFilePath;
    }

    private void loadState() {
        String outputFilePath = getOutputFilePath()+EXT;
        File file = new File(outputFilePath);
        if (file.exists()) {
            try (Reader reader = new FileReader(file)) {
                Gson gson = new Gson();
                AppState appState = gson.fromJson(reader, AppState.class);
                // Restaurer l'état
                this.dayElapsedTime = appState.dayElapsedTime;
                this.currentDate = LocalDate.parse(appState.currentDate);
                ticketTableModel.tickets.clear();
                for (TicketState ts : appState.tickets) {
                    Ticket ticket = new Ticket(ts.jiraId, ts.url, ts.comment,
                            TicketStatus.valueOf(ts.status), ticketTableModel);
                    ticket.elapsedTime = ts.elapsedTime;
                    // Restaurer dailyTimeSpent
                    if (ts.dailyTimeSpent != null) {
                        for (Map.Entry<String, Long> entry : ts.dailyTimeSpent.entrySet()) {
                            LocalDate date = LocalDate.parse(entry.getKey());
                            ticket.dailyTimeSpent.put(date, entry.getValue());
                        }
                    }
                    // Restaurer la liste des todos
                    for (TodoState todoState : ts.todos) {
                        TodoItem todo = new TodoItem(
                                TodoStatus.valueOf(todoState.status),
                                todoState.title,
                                todoState.comment);
                        todo.elapsedTime = todoState.elapsedTime;
                        // Restaurer dailyTimeSpent
                        if (todoState.dailyTimeSpent != null) {
                            for (Map.Entry<String, Long> entry : todoState.dailyTimeSpent.entrySet()) {
                                LocalDate date = LocalDate.parse(entry.getKey());
                                todo.dailyTimeSpent.put(date, entry.getValue());
                            }
                        }
                        todo.setTableModel(todoTableModel);
                        ticket.todoList.add(todo);
                    }
                    ticketTableModel.tickets.add(ticket);
                }
                // Mettre à jour l'interface
                ticketTableModel.fireTableDataChanged();
                updateDayDuration();
                if (!currentDate.equals(LocalDate.now())) {
                    dayElapsedTime = 0;
                    currentDate = LocalDate.now();
                }
                System.out.println("État de l'application chargé depuis le JSON.");
                // Sélectionner le ticket et le todo précédemment sélectionnés
                if (appState.selectedTicketIndex >= 0 && appState.selectedTicketIndex < ticketTableModel.getRowCount()) {
                    ticketTable.setRowSelectionInterval(appState.selectedTicketIndex, appState.selectedTicketIndex);
                }
                if (appState.selectedTodoIndex >= 0 && appState.selectedTodoIndex < todoTableModel.getRowCount()) {
                    todoTable.setRowSelectionInterval(appState.selectedTodoIndex, appState.selectedTodoIndex);
                }
                // Reprendre les timers
                if (!isPaused) {
                    ticketTableModel.resumeSelected();
                    todoTableModel.resumeSelected();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(TimeTrackerApp::new);
    }

    // Classes pour la sauvegarde de l'état
    static class AppState {
        long dayElapsedTime;
        String currentDate;
        List<TicketState> tickets;
        int selectedTicketIndex;
        int selectedTodoIndex;
    }

    static class TicketState {
        String jiraId;
        String url;
        String status;
        String comment;
        long elapsedTime;
        Map<String, Long> dailyTimeSpent;
        List<TodoState> todos;
    }

    static class TodoState {
        String status;
        String title;
        String comment;
        long elapsedTime;
        Map<String, Long> dailyTimeSpent;
    }

    // Enum pour le statut des tickets
    enum TicketStatus {
        TODO,
        IN_PROGRESS,
        BLOCKED,
        DONE
    }

    // Modèle de table personnalisé pour les tickets
    class MyTicketTableModel extends AbstractTableModel {
        private String[] columnNames = {"JIRA ID", "Statut", "Commentaire", "Durée", "Lien"};
        private List<Ticket> tickets = new ArrayList<>();
        private boolean hideDone = false;

        public MyTicketTableModel() {
            // Les tickets seront chargés depuis l'état
        }

        public void removeTicket(int rowIndex) {
            List<Ticket> filteredTickets = getFilteredTickets();
            if (rowIndex >= 0 && rowIndex < filteredTickets.size()) {
                Ticket ticketToRemove = filteredTickets.get(rowIndex);
                tickets.remove(ticketToRemove);
                fireTableRowsDeleted(rowIndex, rowIndex);
            }
        }

        public void addTicket(Ticket ticket) {
            tickets.add(ticket);
            fireTableRowsInserted(tickets.size() - 1, tickets.size() - 1);
        }

        public void pauseAll() {
            for (Ticket ticket : tickets) {
                ticket.pause();
            }
        }

        public void resumeSelected() {
            int selectedRow = ticketTable.getSelectedRow();
            List<Ticket> filteredTickets = getFilteredTickets();
            if (selectedRow >= 0 && selectedRow < filteredTickets.size()) {
                Ticket selectedTicket = filteredTickets.get(selectedRow);
                // Pause all tickets
                pauseAll();
                // Resume selected ticket
                selectedTicket.resume();
            }
        }

        public void setHideDone(boolean hideDone) {
            this.hideDone = hideDone;
            fireTableDataChanged();
        }

        public List<Ticket> getFilteredTickets() {
            List<Ticket> filtered = new ArrayList<>();
            for (Ticket ticket : tickets) {
                if (!(hideDone && ticket.status == TicketStatus.DONE)) {
                    filtered.add(ticket);
                }
            }
            return filtered;
        }

        @Override
        public int getRowCount() {
            // Ajouter une ligne vide pour la saisie
            return getFilteredTickets().size() + 1;
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            List<Ticket> filteredTickets = getFilteredTickets();
            if (rowIndex < filteredTickets.size()) {
                Ticket ticket = filteredTickets.get(rowIndex);
                switch (columnIndex) {
                    case 0:
                        return ticket.jiraId;
                    case 1:
                        return ticket.status;
                    case 2:
                        return ticket.comment;
                    case 3:
                        return formatDuration(ticket.getElapsedTime());
                    case 4:
                        return ticket.url != null ? eyeIcon : null;
                    default:
                        return null;
                }
            } else {
                // Ligne vide
                return null;
            }
        }

        @Override
        public String getColumnName(int col) {
            return columnNames[col];
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            // Permettre l'édition de la dernière ligne vide et des colonnes statut et commentaire des tickets existants
            if (row == getFilteredTickets().size()) {
                return col == 0 || col == 1 || col == 2;
            } else if (col == 0 || col == 1 || col == 2) {
                return true;
            }
            return false;
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            List<Ticket> filteredTickets = getFilteredTickets();
            if (row == filteredTickets.size()) {
                // Édition de la ligne vide
                if (col == 0) {
                    String input = (String) value;
                    if (input != null && !input.trim().isEmpty()) {
                        // Traiter l'entrée
                        String jiraId = extractJiraId(input.trim());
                        String url = extractJiraUrl(input.trim());
                        // Créer un nouveau ticket
                        String comment = (String) getValueAt(row, 2);
                        if (comment == null) comment = "";
                        TicketStatus status = TicketStatus.TODO;
                        Ticket ticket = new Ticket(jiraId, url, comment.trim(), status, this);
                        tickets.add(ticket);
                        fireTableRowsInserted(tickets.size() - 1, tickets.size() - 1);
                    }
                } else if (col == 1 || col == 2) {
                    // Stocker la valeur temporairement
                    fireTableCellUpdated(row, col);
                }
            } else {
                // Édition d'un ticket existant
                Ticket ticket = filteredTickets.get(row);
                switch (col) {
                    case 0:
                        String input = (String) value;
                        if (input != null && !input.trim().isEmpty()) {
                            ticket.jiraId = extractJiraId(input.trim());
                            ticket.url = extractJiraUrl(input.trim());
                        }
                        break;
                    case 1:
                        if (value instanceof TicketStatus) {
                            ticket.status = (TicketStatus) value;
                        }
                        break;
                    case 2:
                        ticket.comment = (String) value;
                        break;
                }
                fireTableCellUpdated(row, col);
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 1) {
                return TicketStatus.class;
            } else if (columnIndex == 4) {
                return ImageIcon.class;
            } else {
                return String.class;
            }
        }

        private String formatDuration(long millis) {
            long seconds = millis / 1000;
            long hh = seconds / 3600;
            long mm = (seconds % 3600) / 60;
            long ss = seconds % 60;
            return String.format("%02d:%02d:%02d", hh, mm, ss);
        }

        // Méthodes pour extraire l'ID Jira et l'URL
        private String extractJiraId(String input) {
            if (input.startsWith("http://") || input.startsWith("https://")) {
                int lastSlash = input.lastIndexOf('/');
                if (lastSlash != -1 && lastSlash < input.length() - 1) {
                    return input.substring(lastSlash + 1);
                }
            }
            return input;
        }

        private String extractJiraUrl(String input) {
            if (input.startsWith("http://") || input.startsWith("https://")) {
                return input;
            }
            // Si ce n'est pas une URL, retourner null ou construire une URL par défaut
            return null;
        }
    }

    // Classe Ticket
    class Ticket {
        String jiraId;
        String url;
        TicketStatus status;
        String comment;
        long startTime = 0;
        long elapsedTime = 0;
        transient Timer timer;
        MyTicketTableModel tableModel;
        List<TodoItem> todoList = new ArrayList<>();

        Map<LocalDate, Long> dailyTimeSpent = new HashMap<>();
        LocalDate currentDate;

        public Ticket(String jiraId, String url, String comment, TicketStatus status, MyTicketTableModel tableModel) {
            this.jiraId = jiraId;
            this.url = url;
            this.comment = comment;
            this.status = status;
            this.tableModel = tableModel;
            initTimer();
        }

        private void initTimer() {
            timer = new Timer(1000, e -> updateDuration());
        }

        public void resume() {
            startTime = System.currentTimeMillis();
            currentDate = LocalDate.now();
            if (timer == null) {
                initTimer();
            }
            timer.start();
        }

        public void pause() {
            if (timer != null && timer.isRunning()) {
                long elapsed = System.currentTimeMillis() - startTime;
                elapsedTime += elapsed;
                // Ajouter le temps écoulé au temps quotidien
                dailyTimeSpent.merge(currentDate, elapsed, Long::sum);
                timer.stop();
            }
        }

        public long getElapsedTime() {
            if (timer != null && timer.isRunning()) {
                return elapsedTime + (System.currentTimeMillis() - startTime);
            } else {
                return elapsedTime;
            }
        }

        public long getElapsedTimeOnDate(LocalDate date) {
            return dailyTimeSpent.getOrDefault(date, 0L);
        }

        private void updateDuration() {
            int index = tableModel.tickets.indexOf(this);
            if (index != -1) {
                tableModel.fireTableRowsUpdated(index, index);
            }
            // Mettre à jour le modèle des durées quotidiennes
            if (ticketTable.getSelectedRow() == index) {
                ticketDateDurationTableModel.setTicket(this);
            }
        }
    }

    // Enum pour le statut des todos
    enum TodoStatus {
        TODO,
        IN_PROGRESS,
        BLOCKED,
        DONE
    }

    // Classe TodoItem
    class TodoItem {
        TodoStatus status;
        String title;
        String comment;
        long startTime = 0;
        long elapsedTime = 0;
        transient Timer timer;
        transient MyTodoTableModel tableModel;

        Map<LocalDate, Long> dailyTimeSpent = new HashMap<>();
        LocalDate currentDate;

        public TodoItem(TodoStatus status, String title, String comment) {
            this.status = status;
            this.title = title;
            this.comment = comment;
            initTimer();
        }

        public void setTableModel(MyTodoTableModel tableModel) {
            this.tableModel = tableModel;
        }

        private void initTimer() {
            timer = new Timer(1000, e -> updateDuration());
        }

        public void resume() {
            startTime = System.currentTimeMillis();
            currentDate = LocalDate.now();
            if (timer == null) {
                initTimer();
            }
            timer.start();
        }

        public void pause() {
            if (timer != null && timer.isRunning()) {
                long elapsed = System.currentTimeMillis() - startTime;
                elapsedTime += elapsed;
                // Ajouter le temps écoulé au temps quotidien
                dailyTimeSpent.merge(currentDate, elapsed, Long::sum);
                timer.stop();
            }
        }

        public long getElapsedTime() {
            if (timer != null && timer.isRunning()) {
                return elapsedTime + (System.currentTimeMillis() - startTime);
            } else {
                return elapsedTime;
            }
        }

        public long getElapsedTimeOnDate(LocalDate date) {
            return dailyTimeSpent.getOrDefault(date, 0L);
        }

        private void updateDuration() {
            if (tableModel != null) {
                int index = tableModel.getIndexOfTodoItem(this);
                if (index != -1) {
                    tableModel.fireTableRowsUpdated(index, index);
                }
                // Mettre à jour le modèle des durées quotidiennes des todos
                if (todoTable.getSelectedRow() == index) {
                    todoDateDurationTableModel.setTodoItem(this);
                }
            }
        }
    }

    // Modèle de table pour la liste des todos
    class MyTodoTableModel extends AbstractTableModel {
        private String[] columnNames = {"Statut", "Titre", "Commentaire", "Durée"};
        private List<TodoItem> todoItems = new ArrayList<>();
        private boolean hideDone = false;

        public void setHideDone(boolean hideDone) {
            this.hideDone = hideDone;
            fireTableDataChanged();
        }

        public void removeTodo(int rowIndex) {
            List<TodoItem> filteredTodoItems = getFilteredItems();
            if (rowIndex >= 0 && rowIndex < filteredTodoItems.size()) {
                TodoItem todoToRemove = filteredTodoItems.get(rowIndex);
                todoItems.remove(todoToRemove);
                fireTableRowsDeleted(rowIndex, rowIndex);
            }
        }

        public void setTodoItems(List<TodoItem> items) {
            // Avant de changer les todos, mettre en pause les todos actuels
            pauseAll();
            todoItems = items;
            // Définir le modèle de table pour chaque TodoItem
            for (TodoItem item : todoItems) {
                item.setTableModel(this);
            }
            fireTableDataChanged();
            // Vider le modèle des durées quotidiennes des todos
            todoDateDurationTableModel.setTodoItem(null);
        }

        public void pauseAll() {
            for (TodoItem todo : todoItems) {
                todo.pause();
            }
        }

        public void resumeSelected() {
            int selectedRow = todoTable.getSelectedRow();
            List<TodoItem> filteredItems = getFilteredItems();
            if (selectedRow >= 0 && selectedRow < filteredItems.size()) {
                TodoItem selectedTodo = filteredItems.get(selectedRow);
                // Pause all todos
                pauseAll();
                // Resume selected todo
                selectedTodo.resume();
            }
        }

        public List<TodoItem> getFilteredItems() {
            List<TodoItem> filtered = new ArrayList<>();
            for (TodoItem item : todoItems) {
                if (!(hideDone && item.status == TodoStatus.DONE)) {
                    filtered.add(item);
                }
            }
            return filtered;
        }

        public int getIndexOfTodoItem(TodoItem item) {
            List<TodoItem> filteredItems = getFilteredItems();
            return filteredItems.indexOf(item);
        }

        @Override
        public int getRowCount() {
            // Ajouter une ligne vide pour la saisie
            return getFilteredItems().size() + 1;
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            List<TodoItem> filteredItems = getFilteredItems();
            if (rowIndex < filteredItems.size()) {
                TodoItem item = filteredItems.get(rowIndex);
                switch (columnIndex) {
                    case 0:
                        return item.status;
                    case 1:
                        return item.title;
                    case 2:
                        return item.comment;
                    case 3:
                        return formatDuration(item.getElapsedTime());
                    default:
                        return null;
                }
            } else {
                // Ligne vide
                return null;
            }
        }

        @Override
        public String getColumnName(int col) {
            return columnNames[col];
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return true;
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            List<TodoItem> filteredItems = getFilteredItems();
            if (row < filteredItems.size()) {
                TodoItem item = filteredItems.get(row);
                switch (col) {
                    case 0:
                        if (value instanceof TodoStatus) {
                            item.status = (TodoStatus) value;
                        }
                        break;
                    case 1:
                        item.title = (String) value;
                        break;
                    case 2:
                        item.comment = (String) value;
                        break;
                }
                fireTableCellUpdated(row, col);
            } else {
                // Ajout d'un nouvel élément
                if (col == 1) {
                    String title = (String) value;
                    if (title != null && !title.trim().isEmpty()) {
                        TodoItem newItem = new TodoItem(TodoStatus.TODO, title.trim(), "");
                        newItem.setTableModel(this);
                        todoItems.add(newItem);
                        fireTableRowsInserted(todoItems.size() - 1, todoItems.size() - 1);
                    }
                }
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) {
                return TodoStatus.class;
            } else {
                return String.class;
            }
        }

        private String formatDuration(long millis) {
            long seconds = millis / 1000;
            long hh = seconds / 3600;
            long mm = (seconds % 3600) / 60;
            long ss = seconds % 60;
            return String.format("%02d:%02d:%02d", hh, mm, ss);
        }
    }

    // Modèle de table pour les durées quotidiennes du ticket sélectionné
    class TicketDateDurationTableModel extends AbstractTableModel {
        private String[] columnNames = {"Date", "Durée"};
        private List<LocalDate> dates = new ArrayList<>();
        private List<Long> durations = new ArrayList<>();

        public void setTicket(Ticket ticket) {
            dates.clear();
            durations.clear();
            if (ticket != null) {
                for (Map.Entry<LocalDate, Long> entry : ticket.dailyTimeSpent.entrySet()) {
                    dates.add(entry.getKey());
                    durations.add(entry.getValue());
                }
            }
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return dates.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                return dates.get(rowIndex).toString();
            } else if (columnIndex == 1) {
                return formatDuration(durations.get(rowIndex));
            }
            return null;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        private String formatDuration(long millis) {
            if (millis == 0) {
                return "";
            }
            long seconds = millis / 1000;
            long hh = seconds / 3600;
            long mm = (seconds % 3600) / 60;
            long ss = seconds % 60;
            return String.format("%02d:%02d:%02d", hh, mm, ss);
        }
    }

    // Modèle de table pour les durées quotidiennes du todo sélectionné
    class TodoDateDurationTableModel extends AbstractTableModel {
        private String[] columnNames = {"Date", "Durée"};
        private List<LocalDate> dates = new ArrayList<>();
        private List<Long> durations = new ArrayList<>();

        public void setTodoItem(TodoItem todoItem) {
            dates.clear();
            durations.clear();
            if (todoItem != null) {
                for (Map.Entry<LocalDate, Long> entry : todoItem.dailyTimeSpent.entrySet()) {
                    dates.add(entry.getKey());
                    durations.add(entry.getValue());
                }
            }
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return dates.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                return dates.get(rowIndex).toString();
            } else if (columnIndex == 1) {
                return formatDuration(durations.get(rowIndex));
            }
            return null;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        private String formatDuration(long millis) {
            if (millis == 0) {
                return "";
            }
            long seconds = millis / 1000;
            long hh = seconds / 3600;
            long mm = (seconds % 3600) / 60;
            long ss = seconds % 60;
            return String.format("%02d:%02d:%02d", hh, mm, ss);
        }
    }

    // Gestionnaire de sélection pour les tickets
    class TicketSelectionHandler implements ListSelectionListener {
        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (!e.getValueIsAdjusting() && !isPaused) {
                int selectedRow = ticketTable.getSelectedRow();
                List<Ticket> filteredTickets = ticketTableModel.getFilteredTickets();

                // Mettre en pause tous les todos du ticket précédent
                if (previousSelectedTicket != null) {
                    for (TodoItem todo : previousSelectedTicket.todoList) {
                        todo.pause();
                    }
                }

                if (selectedRow >= 0 && selectedRow < filteredTickets.size()) {
                    // Mettre en pause tous les tickets
                    ticketTableModel.pauseAll();
                    // Reprendre le chrono du ticket sélectionné
                    Ticket selectedTicket = filteredTickets.get(selectedRow);
                    selectedTicket.resume();
                    // Mettre à jour la liste des todos
                    todoTableModel.setTodoItems(selectedTicket.todoList);

                    // Mettre en pause tous les todos du nouveau ticket
                    todoTableModel.pauseAll();
                    // Mettre à jour le ticket précédent
                    previousSelectedTicket = selectedTicket;

                    // Mettre à jour le modèle du tableau des durées quotidiennes du ticket sélectionné
                    ticketDateDurationTableModel.setTicket(selectedTicket);

                    // Vider le tableau des durées quotidiennes des todos
                    todoDateDurationTableModel.setTodoItem(null);
                } else {
                    // Aucun ticket valide sélectionné
                    todoTableModel.setTodoItems(new ArrayList<>());
                    previousSelectedTicket = null;

                    // Vider les modèles des durées quotidiennes
                    ticketDateDurationTableModel.setTicket(null);
                    todoDateDurationTableModel.setTodoItem(null);
                }
            }
        }
    }

    // Gestionnaire de sélection pour les todos
    class TodoSelectionHandler implements ListSelectionListener {
        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (!e.getValueIsAdjusting() && !isPaused) {
                int selectedRow = todoTable.getSelectedRow();
                List<TodoItem> filteredItems = todoTableModel.getFilteredItems();
                if (selectedRow >= 0 && selectedRow < filteredItems.size()) {
                    // Mettre en pause tous les todos
                    todoTableModel.pauseAll();
                    // Reprendre le chrono du todo sélectionné
                    TodoItem selectedTodo = filteredItems.get(selectedRow);
                    selectedTodo.resume();

                    // Mettre à jour le modèle du tableau des durées quotidiennes du todo sélectionné
                    todoDateDurationTableModel.setTodoItem(selectedTodo);
                } else {
                    // Aucun todo valide sélectionné
                    todoDateDurationTableModel.setTodoItem(null);
                }
            }
        }
    }

    // Renderer personnalisé pour la colonne "Lien"
    class LinkCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            if (value instanceof ImageIcon) {
                JLabel label = new JLabel((ImageIcon) value);
                label.setHorizontalAlignment(CENTER);
                if (isSelected) {
                    label.setOpaque(true);
                    label.setBackground(table.getSelectionBackground());
                }
                return label;
            } else {
                return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            }
        }
    }

    // MouseAdapter pour gérer le clic sur l'icône d'œil
    class TicketMouseAdapter extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            int row = ticketTable.rowAtPoint(e.getPoint());
            int column = ticketTable.columnAtPoint(e.getPoint());
            List<Ticket> filteredTickets = ticketTableModel.getFilteredTickets();
            if (row >= 0 && row < filteredTickets.size() && column == 4) {
                Ticket ticket = filteredTickets.get(row);
                if (ticket.url != null && !ticket.url.isEmpty()) {
                    try {
                        Desktop.getDesktop().browse(new URI(ticket.url));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }
}
