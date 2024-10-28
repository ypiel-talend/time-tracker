package com.github.ypiel.timetracker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimeTrackerApp extends JFrame {

    // Structures de données
    private List<Ticket> tickets = new ArrayList<>();
    private Ticket selectedTicket = null;
    private TodoItem selectedTodo = null;
    private boolean isPaused = false;

    // Composants GUI
    private JTable ticketTable;
    private JTable todoTable;
    private JTable ticketDailyDurationsTable;
    private JTable todoDailyDurationsTable;
    private JButton pauseButton;

    // Timers
    private javax.swing.Timer durationTimer;

    // Chemins des fichiers
    private static final String SAVE_DIR = System.getProperty("time-tracker.dir", System.getProperty("user.home") + "/time-tracker");
    private static final String SAVE_FILE = Paths.get(SAVE_DIR, "time-tracker.json").toString();

    public TimeTrackerApp() {
        setTitle("Time Tracker");
        setSize(1000, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        initGUI();
        loadData();
        updateTicketTable();
        setupTimers();
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                saveData();
            }
        });
    }

    private void initGUI() {
        // Définir le layout
        setLayout(new BorderLayout());

        // Initialiser les tables
        ticketTable = new JTable();
        todoTable = new JTable();
        ticketDailyDurationsTable = new JTable();
        todoDailyDurationsTable = new JTable();

        // Initialiser le bouton pause
        pauseButton = new JButton("Pause");
        pauseButton.setBackground(Color.GREEN.darker());
        pauseButton.addActionListener(e -> togglePause());

        // Configurer les tables
        setupTicketTable();
        setupTodoTable();
        setupTicketDailyDurationsTable();
        setupTodoDailyDurationsTable();

        // Arranger les composants

        // Split pane supérieur (table des tickets et table des todos)
        JSplitPane topSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(ticketTable), new JScrollPane(todoTable));

        // Split pane inférieur (durées quotidiennes des tickets et des todos)
        JSplitPane bottomSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(ticketDailyDurationsTable), new JScrollPane(todoDailyDurationsTable));

        // Split pane principal (haut et bas)
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplitPane, bottomSplitPane);

        // Checkbox to filter the tickets
        JCheckBox jcbTicket = new JCheckBox("Hide Done Tickets");
        jcbTicket.addActionListener(e -> {
            updateTicketTable(jcbTicket.isSelected());
        });

        // Ajouter les composants au frame
        add(mainSplitPane, BorderLayout.CENTER);

        // join pauseButton and jcbTicket in a same panel
        JPanel bottomMenu = new JPanel();
        bottomMenu.setLayout(new FlowLayout(FlowLayout.RIGHT));
        bottomMenu.add(pauseButton);
        bottomMenu.add(jcbTicket);
        add(bottomMenu, BorderLayout.SOUTH);

        // Ajuster les split panes
        topSplitPane.setResizeWeight(0.5);
        bottomSplitPane.setResizeWeight(0.5);
        mainSplitPane.setResizeWeight(0.7);
    }

    private void setupTicketTable() {
        String[] columns = {"ID", "Description", "Status", "Duration", "Open", "Delete"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int row, int column) {
                // Permettre l'édition des colonnes ID et Description
                return column == 0 || column == 1 || column == 2 || column == 4 || column == 5;
            }
        };
        ticketTable.setModel(model);

        // Ajouter une ligne vide pour un nouveau ticket
        model.addRow(new Object[]{"", "", Status.New, "", "", ""});

        // Renderer et éditeur pour les boutons Open et Delete
        ticketTable.getColumn("Open").setCellRenderer(new ButtonRenderer());
        ticketTable.getColumn("Delete").setCellRenderer(new ButtonRenderer());

        ticketTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = ticketTable.rowAtPoint(e.getPoint());
                int col = ticketTable.columnAtPoint(e.getPoint());

                if (col == 5) { // Check if "Label" column is clicked
                    tickets.remove(row);
                    model.removeRow(row);
                } else if (col == 4) {
                    String url = (String) model.getValueAt(row, 0);
                    if (url != null && url.startsWith("http")) {
                        try {
                            Desktop.getDesktop().browse(new URI(url));
                        } catch (Exception ex) {
                            throw new RuntimeException("Can't open the URL in the browser: " + url, ex);
                        }
                    }
                }
            }
        });

        // Renderer pour le statut
        ticketTable.getColumn("Status").

                setCellEditor(new DefaultCellEditor(new JComboBox<>(Status.values())));

        // Listener pour ajouter un nouveau ticket en appuyant sur ENTER
        ticketTable.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                        .

                put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "AddTicket");

        ticketTable.getColumnModel().

                getColumn(0).

                setCellRenderer(new DefaultTableCellRenderer() {
                    @Override
                    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                                   boolean hasFocus, int row, int column) {
                        String v = (String) value;
                        if (v.startsWith("http")) {
                            int lastSegmentIndex = v.lastIndexOf('/');

                            v = lastSegmentIndex > 0 ? v.substring(lastSegmentIndex + 1) : v;
                        }

                        return super.getTableCellRendererComponent(table, v, isSelected, hasFocus, row, column);

                    }

                });

        ticketTable.getActionMap().

                put("AddTicket", new AbstractAction() {
                    public void actionPerformed(ActionEvent e) {
                        int row = ticketTable.getSelectedRow();
                        if (row == ticketTable.getRowCount() - 1) {
                            String id = (String) model.getValueAt(row, 0);
                            String description = (String) model.getValueAt(row, 1);
                            Status status = (Status) model.getValueAt(row, 2);
                            if (id != null && !id.trim().isEmpty()) {
                                Ticket ticket = new Ticket(id.trim(), description != null ? description.trim() : "", status);
                                tickets.add(ticket);
                                model.insertRow(model.getRowCount() - 1, new Object[]{id.trim(), description, status, "00:00:00", "Open", "Delete"});
                                model.setValueAt("", model.getRowCount() - 1, 0);
                                model.setValueAt("", model.getRowCount() - 1, 1);
                                model.setValueAt(Status.New, model.getRowCount() - 1, 2);
                            }
                        }
                    }
                });

        // Listener de sélection pour ticketTable
        ticketTable.getSelectionModel().

                addListSelectionListener(e ->

                {
                    if (!e.getValueIsAdjusting()) {
                        int index = ticketTable.getSelectedRow();
                        if (index >= 0 && index < tickets.size()) {
                            selectedTicket = tickets.get(index);
                            selectedTodo = null;
                            updateTodoTable();
                            updateTicketDailyDurationsTable();
                            updateTodoDailyDurationsTable();
                        } else {
                            selectedTicket = null;
                            selectedTodo = null;
                            clearTodoTable();
                            clearTicketDailyDurationsTable();
                            clearTodoDailyDurationsTable();
                        }
                    }
                });

        // Listener pour mettre à jour le statut du ticket
        model.addTableModelListener(e ->

        {
            int row = e.getFirstRow();
            int column = e.getColumn();
            if (column == 2 && row >= 0 && row < tickets.size()) {
                Ticket ticket = tickets.get(row);
                Status newStatus = (Status) model.getValueAt(row, column);
                ticket.setStatus(newStatus);
            }
        });
    }

    private void setupTodoTable() {
        String[] columns = {"Status", "Description", "Duration", "Delete"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int row, int column) {
                // Permettre l'édition des colonnes Status et Description
                return column == 0 || column == 1 || column == 3;
            }
        };
        todoTable.setModel(model);

        // Ajouter une ligne vide pour un nouveau todo
        model.addRow(new Object[]{Status.New, "", "", ""});

        // Renderer et éditeur pour le bouton Delete
        todoTable.getColumn("Delete").setCellRenderer(new ButtonRenderer());
        todoTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = todoTable.rowAtPoint(e.getPoint());
                int col = todoTable.columnAtPoint(e.getPoint());

                if (col == 3) {
                    int selectedTicketRow = ticketTable.getSelectedRow();
                    if (selectedTicket != null && selectedTicketRow >= 0 && selectedTicketRow < tickets.size()) {
                        selectedTicket.getTodoItems().remove(row);
                        updateTodoTable();
                    }
                }
            }
        });

        // Renderer pour le statut
        todoTable.getColumn("Status").setCellEditor(new DefaultCellEditor(new JComboBox<>(Status.values())));

        // Listener pour ajouter un nouveau todo en appuyant sur ENTER
        todoTable.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "AddTodo");
        todoTable.getActionMap().put("AddTodo", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                int row = todoTable.getSelectedRow();
                if (selectedTicket != null && row == todoTable.getRowCount() - 1) {
                    Status status = (Status) model.getValueAt(row, 0);
                    String description = (String) model.getValueAt(row, 1);
                    if (description != null && !description.trim().isEmpty()) {
                        TodoItem todo = new TodoItem(description.trim(), status);
                        selectedTicket.getTodoItems().add(todo);
                        model.insertRow(model.getRowCount() - 1, new Object[]{status, description, "00:00:00", "Delete"});
                        model.setValueAt(Status.New, model.getRowCount() - 1, 0);
                        model.setValueAt("", model.getRowCount() - 1, 1);
                    }
                }
            }
        });

        // Listener de sélection pour todoTable
        todoTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int index = todoTable.getSelectedRow();
                if (selectedTicket != null && index >= 0 && index < selectedTicket.getTodoItems().size()) {
                    selectedTodo = selectedTicket.getTodoItems().get(index);
                    updateTodoDailyDurationsTable();
                } else {
                    selectedTodo = null;
                    clearTodoDailyDurationsTable();
                }
            }
        });

        // Listener pour mettre à jour le statut du todo
        model.addTableModelListener(e -> {
            int row = e.getFirstRow();
            int column = e.getColumn();
            if (column == 0 && selectedTicket != null && row >= 0 && row < selectedTicket.getTodoItems().size()) {
                TodoItem todo = selectedTicket.getTodoItems().get(row);
                Status newStatus = (Status) model.getValueAt(row, column);
                todo.setStatus(newStatus);
            }
        });
    }

    private void setupTicketDailyDurationsTable() {
        String[] columns = {"Jour", "Duration"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        ticketDailyDurationsTable.setModel(model);
    }

    private void setupTodoDailyDurationsTable() {
        String[] columns = {"Jour", "Duration"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        todoDailyDurationsTable.setModel(model);
    }

    private void updateTicketTable() {
        updateTicketTable(false);
    }

    private void updateTicketTable(boolean hideDone) {
        DefaultTableModel model = (DefaultTableModel) ticketTable.getModel();
        model.setRowCount(0);
        for (Ticket ticket : tickets) {

            if (hideDone && ticket.status == Status.Done) {
                continue;
            }

            model.addRow(new Object[]{ticket.getId(), ticket.getDescription(), ticket.getStatus(),
                    formatDuration(ticket.getDuration()), "Open", "Delete"});
        }
        model.addRow(new Object[]{"", "", Status.New, "", "", ""});
    }

    private void updateTodoTable() {
        DefaultTableModel model = (DefaultTableModel) todoTable.getModel();
        model.setRowCount(0);
        if (selectedTicket != null) {
            for (TodoItem todo : selectedTicket.getTodoItems()) {
                model.addRow(new Object[]{todo.getStatus(), todo.getDescription(), formatDuration(todo.getDuration()), "Delete"});
            }
            model.addRow(new Object[]{Status.New, "", "", ""});
        }
    }

    private void clearTodoTable() {
        DefaultTableModel model = (DefaultTableModel) todoTable.getModel();
        model.setRowCount(0);
    }

    private void updateTicketDailyDurationsTable() {
        DefaultTableModel model = (DefaultTableModel) ticketDailyDurationsTable.getModel();
        model.setRowCount(0);
        if (selectedTicket != null) {
            Map<String, Long> durationsPerDay = selectedTicket.getDurationsPerDay();
            for (Map.Entry<String, Long> entry : durationsPerDay.entrySet()) {
                model.addRow(new Object[]{entry.getKey(), formatDuration(entry.getValue())});
            }
        }
    }

    private void clearTicketDailyDurationsTable() {
        DefaultTableModel model = (DefaultTableModel) ticketDailyDurationsTable.getModel();
        model.setRowCount(0);
    }

    private void updateTodoDailyDurationsTable() {
        DefaultTableModel model = (DefaultTableModel) todoDailyDurationsTable.getModel();
        model.setRowCount(0);
        if (selectedTodo != null) {
            Map<String, Long> durationsPerDay = selectedTodo.getDurationsPerDay();
            for (Map.Entry<String, Long> entry : durationsPerDay.entrySet()) {
                model.addRow(new Object[]{entry.getKey(), formatDuration(entry.getValue())});
            }
        }
    }

    private void clearTodoDailyDurationsTable() {
        DefaultTableModel model = (DefaultTableModel) todoDailyDurationsTable.getModel();
        model.setRowCount(0);
    }

    private void setupTimers() {
        final AtomicLong last = new AtomicLong(System.currentTimeMillis());
        durationTimer = new javax.swing.Timer(1000, e -> {
            final long current = System.currentTimeMillis();
            final long elapsedSecond = (current - last.get()) / 1000;
            last.set(current);
            if (!isPaused) {
                String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
                if (selectedTicket != null) {
                    selectedTicket.incrementDuration(elapsedSecond);
                    selectedTicket.incrementDurationForDay(today, elapsedSecond);
                    updateTicketDuration();
                    updateTicketDailyDurationsTable();
                }
                if (selectedTodo != null) {
                    selectedTodo.incrementDuration(elapsedSecond);
                    selectedTodo.incrementDurationForDay(today, elapsedSecond);
                    updateTodoDuration();
                    updateTodoDailyDurationsTable();
                }
            }
        });
        durationTimer.start();

        // Timer pour sauvegarder les données chaque minute
        new javax.swing.Timer(60000, e -> saveData()).start();
    }

    private void updateTicketDuration() {
        int index = tickets.indexOf(selectedTicket);
        if (index >= 0) {
            DefaultTableModel model = (DefaultTableModel) ticketTable.getModel();
            model.setValueAt(formatDuration(selectedTicket.getDuration()), index, 3);
        }
    }

    private void updateTodoDuration() {
        if (selectedTicket != null && selectedTodo != null) {
            int index = selectedTicket.getTodoItems().indexOf(selectedTodo);
            if (index >= 0) {
                DefaultTableModel model = (DefaultTableModel) todoTable.getModel();
                model.setValueAt(formatDuration(selectedTodo.getDuration()), index, 2);
            }
        }
    }

    private void togglePause() {
        isPaused = !isPaused;
        if (isPaused) {
            pauseButton.setText("Reprendre");
            pauseButton.setBackground(Color.YELLOW);
        } else {
            pauseButton.setText("Pause");
            pauseButton.setBackground(Color.GREEN.darker());
        }
    }

    private String formatDuration(long seconds) {
        long hrs = seconds / 3600;
        long mins = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hrs, mins, secs);
    }

    private void saveData() {
        try {
            log.info("Save time-tracker data to {}.", SAVE_FILE);
            Files.createDirectories(Paths.get(SAVE_DIR));
            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(new File(SAVE_FILE), tickets);
            // Copier dans un fichier avec le jour de l'année
            String dayOfYear = String.valueOf(LocalDate.now().getDayOfYear());
            String backupFile = SAVE_DIR + "/time-tracker-" + dayOfYear + ".json";
            Files.copy(Paths.get(SAVE_FILE), Paths.get(backupFile), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadData() {
        File file = new File(SAVE_FILE);
        if (file.exists()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                tickets = mapper.readValue(file, new TypeReference<List<Ticket>>() {
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Enum pour les statuts
    public enum Status {
        New,
        In_Progress,
        On_Hold,
        Validation,
        Done;
    }

    // Classes internes pour les modèles de données
    static class Ticket {
        private String id;
        private String description;
        private Status status;
        private long duration; // en secondes
        private Map<String, Long> durationsPerDay;
        private List<TodoItem> todoItems;

        public Ticket() {
            this.durationsPerDay = new HashMap<>();
            this.todoItems = new ArrayList<>();
        }

        public Ticket(String id, String description, Status status) {
            this.id = id;
            this.description = description;
            this.status = status;
            this.duration = 0;
            this.durationsPerDay = new HashMap<>();
            this.todoItems = new ArrayList<>();
        }

        public void incrementDuration(long elapsedSeconds) {
            duration += elapsedSeconds;
        }

        public void incrementDurationForDay(String day, long elapsedSeconds) {
            durationsPerDay.put(day, durationsPerDay.getOrDefault(day, 0L) + elapsedSeconds);
        }

        // Getters et setters

        public String getId() {
            return id;
        }

        public String getDescription() {
            return description;
        }

        public Status getStatus() {
            return status;
        }

        public long getDuration() {
            return duration;
        }

        public Map<String, Long> getDurationsPerDay() {
            return durationsPerDay;
        }

        public List<TodoItem> getTodoItems() {
            return todoItems;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public void setStatus(Status status) {
            this.status = status;
        }

        public void setDuration(long duration) {
            this.duration = duration;
        }

        public void setDurationsPerDay(Map<String, Long> durationsPerDay) {
            this.durationsPerDay = durationsPerDay;
        }

        public void setTodoItems(List<TodoItem> todoItems) {
            this.todoItems = todoItems;
        }
    }

    static class TodoItem {
        private Status status;
        private String description;
        private long duration; // en secondes
        private Map<String, Long> durationsPerDay;

        public TodoItem() {
            this.durationsPerDay = new HashMap<>();
        }

        public TodoItem(String description, Status status) {
            this.description = description;
            this.status = status;
            this.duration = 0;
            this.durationsPerDay = new HashMap<>();
        }

        public void incrementDuration(long elapsedSecond) {
            duration += elapsedSecond;
        }

        public void incrementDurationForDay(String day, long elapsedSecond) {
            durationsPerDay.put(day, durationsPerDay.getOrDefault(day, 0L) + elapsedSecond);
        }

        // Getters et setters

        public Status getStatus() {
            return status;
        }

        public String getDescription() {
            return description;
        }

        public long getDuration() {
            return duration;
        }

        public Map<String, Long> getDurationsPerDay() {
            return durationsPerDay;
        }

        public void setStatus(Status status) {
            this.status = status;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public void setDuration(long duration) {
            this.duration = duration;
        }

        public void setDurationsPerDay(Map<String, Long> durationsPerDay) {
            this.durationsPerDay = durationsPerDay;
        }
    }

    // Renderer pour les boutons
    class ButtonRenderer extends JButton implements TableCellRenderer {
        public ButtonRenderer() {
            setOpaque(true);
        }

        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            setText((value == null) ? "" : value.toString());
            return this;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TimeTrackerApp().setVisible(true));
    }
}
