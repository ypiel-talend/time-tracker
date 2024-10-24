package com.github.ypiel.timetracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.*;
import java.util.*;

import com.google.gson.*;

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
    private static final String STATE_FILE = "state.json";

    public TimeTrackerApp() {
        super("Time Tracker");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 600);
        setLocationRelativeTo(null);

        // Initialiser les composants
        ticketTableModel = new MyTicketTableModel();
        ticketTable = new JTable(ticketTableModel);

        // Définir l'éditeur personnalisé pour la colonne de statut des tickets
        JComboBox<TicketStatus> ticketStatusComboBox = new JComboBox<>(TicketStatus.values());
        ticketTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(ticketStatusComboBox));

        // Ajouter un ListSelectionListener au tableau des tickets
        ticketTable.getSelectionModel().addListSelectionListener(new TicketSelectionHandler());

        // Configurer le comportement d'édition du tableau des tickets
        ticketTable.setSurrendersFocusOnKeystroke(true);

        JScrollPane ticketScrollPane = new JScrollPane(ticketTable);

        hideTicketDoneCheckBox = new JCheckBox("Cacher les tickets terminés");
        hideTicketDoneCheckBox.addActionListener(e -> ticketTableModel.setHideDone(hideTicketDoneCheckBox.isSelected()));

        JPanel ticketTopPanel = new JPanel(new BorderLayout());
        ticketTopPanel.add(hideTicketDoneCheckBox, BorderLayout.WEST);

        JPanel ticketPanel = new JPanel(new BorderLayout());
        ticketPanel.add(ticketTopPanel, BorderLayout.NORTH);
        ticketPanel.add(ticketScrollPane, BorderLayout.CENTER);

        pauseButton = new JButton("Pause");
        pauseButton.setBackground(Color.RED);
        pauseButton.setForeground(Color.WHITE);
        pauseButton.setFont(new Font("Arial", Font.BOLD, 24));
        pauseButton.addActionListener(e -> togglePause());

        dayDurationLabel = new JLabel("Durée de travail aujourd'hui : 00:00:00");
        dayDurationLabel.setFont(new Font("Arial", Font.PLAIN, 18));

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BorderLayout());
        bottomPanel.add(pauseButton, BorderLayout.CENTER);
        bottomPanel.add(dayDurationLabel, BorderLayout.EAST);

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

        JPanel todoPanel = new JPanel(new BorderLayout());
        todoPanel.add(todoTopPanel, BorderLayout.NORTH);
        todoPanel.add(todoScrollPane, BorderLayout.CENTER);

        // Ajouter un ListSelectionListener au tableau des todos
        todoTable.getSelectionModel().addListSelectionListener(new TodoSelectionHandler());

        // Split pane pour contenir les deux tableaux
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, ticketPanel, todoPanel);
        splitPane.setDividerLocation(500);

        getContentPane().setLayout(new BorderLayout());
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

        setVisible(true);
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
        try (Writer writer = new FileWriter(STATE_FILE)) {
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
                ts.status = ticket.status.name();
                ts.comment = ticket.comment;
                ts.elapsedTime = ticket.elapsedTime;
                if (!isPaused && ticket.timer.isRunning()) {
                    ts.elapsedTime += System.currentTimeMillis() - ticket.startTime;
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
                    ts.todos.add(todoState);
                }
                appState.tickets.add(ts);
            }
            // Sauvegarder l'index du ticket sélectionné
            appState.selectedTicketIndex = ticketTable.getSelectedRow();
            appState.selectedTodoIndex = todoTable.getSelectedRow();
            gson.toJson(appState, writer);
            System.out.println("État de l'application sauvegardé en JSON.");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void loadState() {
        File file = new File(STATE_FILE);
        if (file.exists()) {
            try (Reader reader = new FileReader(STATE_FILE)) {
                Gson gson = new Gson();
                AppState appState = gson.fromJson(reader, AppState.class);
                // Restaurer l'état
                this.dayElapsedTime = appState.dayElapsedTime;
                this.currentDate = LocalDate.parse(appState.currentDate);
                ticketTableModel.tickets.clear();
                for (TicketState ts : appState.tickets) {
                    Ticket ticket = new Ticket(ts.jiraId, ts.comment, TicketStatus.valueOf(ts.status), ticketTableModel);
                    ticket.elapsedTime = ts.elapsedTime;
                    // Restaurer la liste des todos
                    for (TodoState todoState : ts.todos) {
                        TodoItem todo = new TodoItem(
                                TodoStatus.valueOf(todoState.status),
                                todoState.title,
                                todoState.comment);
                        todo.elapsedTime = todoState.elapsedTime;
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
        String status;
        String comment;
        long elapsedTime;
        List<TodoState> todos;
    }

    static class TodoState {
        String status;
        String title;
        String comment;
        long elapsedTime;
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
        private String[] columnNames = {"JIRA ID", "Statut", "Commentaire", "Durée"};
        private List<Ticket> tickets = new ArrayList<>();
        private boolean hideDone = false;

        public MyTicketTableModel() {
            // Les tickets seront chargés depuis l'état
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
            } else if (col == 1 || col == 2) {
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
                    String jiraId = (String) value;
                    if (jiraId != null && !jiraId.trim().isEmpty()) {
                        // Créer un nouveau ticket
                        String comment = (String) getValueAt(row, 2);
                        if (comment == null) comment = "";
                        TicketStatus status = TicketStatus.TODO;
                        Ticket ticket = new Ticket(jiraId.trim(), comment.trim(), status, this);
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

    // Classe Ticket
    class Ticket {
        String jiraId;
        TicketStatus status;
        String comment;
        long startTime = 0;
        long elapsedTime = 0;
        transient Timer timer;
        MyTicketTableModel tableModel;
        List<TodoItem> todoList = new ArrayList<>();

        public Ticket(String jiraId, String comment, TicketStatus status, MyTicketTableModel tableModel) {
            this.jiraId = jiraId;
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
            if (timer == null) {
                initTimer();
            }
            timer.start();
        }

        public void pause() {
            if (timer != null && timer.isRunning()) {
                elapsedTime += System.currentTimeMillis() - startTime;
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

        private void updateDuration() {
            int index = tableModel.tickets.indexOf(this);
            if (index != -1) {
                tableModel.fireTableRowsUpdated(index, index);
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
            if (timer == null) {
                initTimer();
            }
            timer.start();
        }

        public void pause() {
            if (timer != null && timer.isRunning()) {
                elapsedTime += System.currentTimeMillis() - startTime;
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

        private void updateDuration() {
            if (tableModel != null) {
                int index = tableModel.getIndexOfTodoItem(this);
                if (index != -1) {
                    tableModel.fireTableRowsUpdated(index, index);
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

        public void setTodoItems(List<TodoItem> items) {
            todoItems = items;
            // Définir le modèle de table pour chaque TodoItem
            for (TodoItem item : todoItems) {
                item.setTableModel(this);
            }
            fireTableDataChanged();
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

    // Gestionnaire de sélection pour les tickets
    class TicketSelectionHandler implements ListSelectionListener {
        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (!e.getValueIsAdjusting() && !isPaused) {
                int selectedRow = ticketTable.getSelectedRow();
                List<Ticket> filteredTickets = ticketTableModel.getFilteredTickets();
                if (selectedRow >= 0 && selectedRow < filteredTickets.size()) {
                    // Mettre en pause tous les tickets
                    ticketTableModel.pauseAll();
                    // Reprendre le chrono du ticket sélectionné
                    Ticket selectedTicket = filteredTickets.get(selectedRow);
                    selectedTicket.resume();
                    // Mettre à jour la liste des todos
                    todoTableModel.setTodoItems(selectedTicket.todoList);
                } else {
                    // Aucun ticket valide sélectionné
                    todoTableModel.setTodoItems(new ArrayList<>());
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
                }
            }
        }
    }
}
