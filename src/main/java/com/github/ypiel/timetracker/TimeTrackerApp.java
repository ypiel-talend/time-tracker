package com.github.ypiel.timetracker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimeTrackerApp extends JFrame {
    private final static int TICKET_TABLE_COLUMN_ORDER = 0;
    private final static int TICKET_TABLE_COLUMN_ID = 1;
    private final static int TICKET_TABLE_COLUMN_DESC = 2;
    private final static int TICKET_TABLE_COLUMN_STATUS = 3;
    private final static int TICKET_TABLE_COLUMN_DURATION = 4;
    private final static int TICKET_TABLE_COLUMN_OPEN = 5;
    private final static int TICKET_TABLE_COLUMN_DELETE = 6;

    private final static int TODO_TABLE_COLUMN_STATUS = 0;
    private final static int TODO_TABLE_COLUMN_DESC = 1;
    private final static int TODO_TABLE_COLUMN_DURATION = 2;
    private final static int TODO_TABLE_COLUMN_DELETE = 3;

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

    private JCheckBox jcbTicket;
    private JCheckBox jcbTodo;

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
        jcbTicket = new JCheckBox("Hide Done Tickets");
        jcbTicket.setSelected(true);
        jcbTicket.addActionListener(e -> {
            updateTicketTable();
        });

        // Checkbox to filter the todos
        jcbTodo = new JCheckBox("Hide Done Todos");
        jcbTodo.setSelected(true);
        jcbTodo.addActionListener(e -> {
            updateTodoTable();
        });

        // Ajouter les composants au frame
        add(mainSplitPane, BorderLayout.CENTER);

        // join pauseButton and jcbTicket in a same panel
        JPanel bottomMenu = new JPanel();
        bottomMenu.setLayout(new FlowLayout(FlowLayout.RIGHT));
        bottomMenu.add(pauseButton);
        bottomMenu.add(jcbTicket);
        bottomMenu.add(jcbTodo);
        add(bottomMenu, BorderLayout.SOUTH);

        // Ajuster les split panes
        topSplitPane.setResizeWeight(0.5);
        bottomSplitPane.setResizeWeight(0.5);
        mainSplitPane.setResizeWeight(0.7);
    }

    private void setupTicketTable() {
        String[] columns = {"Order", "ID", "Description", "Status", "Duration", "Open", "Delete"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int row, int column) {
                // Permettre l'édition des colonnes ID et Description
                return column == TICKET_TABLE_COLUMN_ORDER ||
                        column == TICKET_TABLE_COLUMN_ID || column == TICKET_TABLE_COLUMN_DESC ||
                        column == TICKET_TABLE_COLUMN_STATUS || column == TICKET_TABLE_COLUMN_OPEN ||
                        column == TICKET_TABLE_COLUMN_DELETE;
            }
        };
        ticketTable.setModel(model);

        // Ajouter une ligne vide pour un nouveau ticket
        model.addRow(new Object[]{0, "", "", Status.New, "", "", ""});

        ticketTable.getColumnModel().getColumn(TICKET_TABLE_COLUMN_ORDER).setCellEditor(new IntegerEditor());

        // Renderer et éditeur pour les boutons Open et Delete
        ticketTable.getColumn("Open").setCellRenderer(new ButtonRenderer());
        ticketTable.getColumn("Delete").setCellRenderer(new ButtonRenderer());

        ticketTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = ticketTable.convertRowIndexToModel(ticketTable.rowAtPoint(e.getPoint()));
                int col = ticketTable.columnAtPoint(e.getPoint());

                String toRemove = (String) model.getValueAt(row, TICKET_TABLE_COLUMN_ID);

                if (col == TICKET_TABLE_COLUMN_DELETE) { // Check if "Label" column is clicked
                    tickets.stream().filter(t -> t.getId().equals(toRemove)).findAny().ifPresent(o -> {
                        tickets.remove(o);
                        model.removeRow(row);
                    });

                } else if (col == TICKET_TABLE_COLUMN_OPEN) {
                    String url = (String) model.getValueAt(row, TICKET_TABLE_COLUMN_ID);
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
        ticketTable.getColumnModel().getColumn(TICKET_TABLE_COLUMN_STATUS).setCellEditor(new DefaultCellEditor(new JComboBox<>(Status.values())));

        // Listener pour ajouter un nouveau ticket en appuyant sur ENTER
        ticketTable.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "AddTicket");

        ticketTable.getColumnModel().getColumn(TICKET_TABLE_COLUMN_ID).setCellRenderer(new DefaultTableCellRenderer() {
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
                        int row = ticketTable.convertRowIndexToModel(ticketTable.getSelectedRow());

                        // if (row == ticketTable.getRowCount() - 1) {
                        int order = (Integer) model.getValueAt(row, TICKET_TABLE_COLUMN_ORDER);

                        if (order <= 0) {
                            JOptionPane.showMessageDialog(null, "Ticket order must be higher than 0.", "Warning", JOptionPane.WARNING_MESSAGE);
                            return;
                        }

                        String id = (String) model.getValueAt(row, TICKET_TABLE_COLUMN_ID);
                        String description = (String) model.getValueAt(row, TICKET_TABLE_COLUMN_DESC);
                        Status status = (Status) model.getValueAt(row, TICKET_TABLE_COLUMN_STATUS);

                        if (id != null && !id.trim().isEmpty() && row == ticketTable.getRowCount() - 1) {
                            Optional<Ticket> exists = tickets.stream().filter(t -> t.getId().equals(id)).findAny();
                            if (exists.isPresent()) {
                                JOptionPane.showMessageDialog(null, "Ticket with ID " + id + " already exists.", "Warning", JOptionPane.WARNING_MESSAGE);
                                return;
                            }


                            Ticket ticket = new Ticket(order, id.trim(), description != null ? description.trim() : "", status);
                            tickets.add(ticket);
                            model.insertRow(model.getRowCount() - 1, new Object[]{order, id.trim(), description, status, "00:00:00", "Open", "Delete"});
                            model.setValueAt(0, model.getRowCount() - 1, TICKET_TABLE_COLUMN_ORDER);
                            model.setValueAt("", model.getRowCount() - 1, TICKET_TABLE_COLUMN_ID);
                            model.setValueAt("", model.getRowCount() - 1, TICKET_TABLE_COLUMN_DESC);
                            model.setValueAt(Status.New, model.getRowCount() - 1, TICKET_TABLE_COLUMN_STATUS);
                        } else {
                            Ticket selected = tickets.get(row);

                            if (!id.equals(selected.getId())) {
                                Optional<Ticket> exists = tickets.stream().filter(t -> t.getId().equals(id)).findAny();
                                if (exists.isPresent()) {
                                    JOptionPane.showMessageDialog(null, "Ticket with ID " + id + " already exists.", "Warning", JOptionPane.WARNING_MESSAGE);
                                    return;
                                }
                            }

                            selected.setOrder(order);
                            selected.setId(id);
                            selected.setDescription(description);
                            selected.setStatus(status);
                        }
                        //}
                    }
                });

        // Listener de sélection pour ticketTable
        ticketTable.getSelectionModel().
                addListSelectionListener(e -> {
                    if (!e.getValueIsAdjusting()) {
                        int index = ticketTable.convertRowIndexToModel(ticketTable.getSelectedRow());
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
        model.addTableModelListener(e -> {
            int row = e.getFirstRow();
            int column = e.getColumn();
            if (column == TICKET_TABLE_COLUMN_STATUS && row >= 0 && row < tickets.size()) {
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
                return column == TODO_TABLE_COLUMN_STATUS || column == TODO_TABLE_COLUMN_DESC || column == TODO_TABLE_COLUMN_DELETE;
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

                if (col == TODO_TABLE_COLUMN_DELETE) {
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
                    Status status = (Status) model.getValueAt(row, TODO_TABLE_COLUMN_STATUS);
                    String description = (String) model.getValueAt(row, TODO_TABLE_COLUMN_DESC);
                    if (description != null && !description.trim().isEmpty()) {
                        TodoItem todo = new TodoItem(description.trim(), status);
                        selectedTicket.getTodoItems().add(todo);
                        model.insertRow(model.getRowCount() - 1, new Object[]{status, description, "00:00:00", "Delete"});
                        model.setValueAt(Status.New, model.getRowCount() - 1, TODO_TABLE_COLUMN_STATUS);
                        model.setValueAt("", model.getRowCount() - 1, TODO_TABLE_COLUMN_DESC);
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
            if (column == TODO_TABLE_COLUMN_STATUS && selectedTicket != null && row >= 0 && row < selectedTicket.getTodoItems().size()) {
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
        DefaultTableModel model = (DefaultTableModel) ticketTable.getModel();
        model.setRowCount(0);
        for (Ticket ticket : tickets) {

            if (jcbTicket.isSelected() && ticket.status == Status.Done) {
                continue;
            }

            model.addRow(new Object[]{ticket.getOrder(), ticket.getId(), ticket.getDescription(), ticket.getStatus(),
                    formatDuration(ticket.getDuration()), "Open", "Delete"});
        }
        model.addRow(new Object[]{0, "", "", Status.New, "", "", ""});

        TableRowSorter<TableModel> sorter = new TableRowSorter<>(ticketTable.getModel());
        sorter.setComparator(0, Comparator.comparingInt((Integer o) -> o));

        ticketTable.setRowSorter(sorter);
    }

    private void updateTodoTable() {
        DefaultTableModel model = (DefaultTableModel) todoTable.getModel();
        model.setRowCount(0);
        if (selectedTicket != null) {
            for (TodoItem todo : selectedTicket.getTodoItems()) {
                if (jcbTodo.isSelected() && todo.status == Status.Done) {
                    continue;
                }
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
            model.setValueAt(formatDuration(selectedTicket.getDuration()), index, TICKET_TABLE_COLUMN_DURATION);
        }
    }

    private void updateTodoDuration() {
        if (selectedTicket != null && selectedTodo != null) {
            int index = selectedTicket.getTodoItems().indexOf(selectedTodo);
            if (index >= 0) {
                DefaultTableModel model = (DefaultTableModel) todoTable.getModel();
                model.setValueAt(formatDuration(selectedTodo.getDuration()), index, TODO_TABLE_COLUMN_DURATION);
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
        log.info("time-tracker data loaded from {}.", SAVE_FILE);
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
    @Data
    static class Ticket {
        private int order = 0;
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

        public Ticket(int order, String id, String description, Status status) {
            this.order = order;
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
    }


    @Data
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

    }

    // Renderer pour les boutons
    static class ButtonRenderer extends JButton implements TableCellRenderer {
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

    static class IntegerEditor extends AbstractCellEditor implements TableCellEditor {
        private final JTextField textField = new JTextField();

        public IntegerEditor() {
            // Listen for key input to allow only numbers
            textField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyTyped(KeyEvent e) {
                    char c = e.getKeyChar();
                    if (!Character.isDigit(c)) {
                        e.consume();
                    }
                }
            });
        }

        @Override
        public Object getCellEditorValue() {
            try {
                return Integer.parseInt(textField.getText());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            textField.setText(value != null ? value.toString() : "");
            return textField;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TimeTrackerApp().setVisible(true));
    }
}
