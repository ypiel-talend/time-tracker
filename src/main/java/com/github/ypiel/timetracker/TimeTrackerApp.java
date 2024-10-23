package com.github.ypiel.timetracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
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
    private int lastRunningTicketIndex = -1;
    private JCheckBox hideDoneCheckBox;

    // For periodic saving
    private Timer saveTimer;
    private static final String STATE_FILE = "state.json";

    public TimeTrackerApp() {
        super("Time Tracker");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 600);
        setLocationRelativeTo(null);

        // Initialize components
        ticketTableModel = new MyTicketTableModel();
        ticketTable = new JTable(ticketTableModel);

        // Add ListSelectionListener to the ticket table
        ticketTable.getSelectionModel().addListSelectionListener(new TicketSelectionHandler());

        // Set up ticket table editing behavior
        ticketTable.setSurrendersFocusOnKeystroke(true);

        JScrollPane ticketScrollPane = new JScrollPane(ticketTable);

        pauseButton = new JButton("Pause");
        pauseButton.setBackground(Color.RED);
        pauseButton.setForeground(Color.WHITE);
        pauseButton.setFont(new Font("Arial", Font.BOLD, 24));
        pauseButton.addActionListener(e -> togglePause());

        dayDurationLabel = new JLabel("Durée de travail aujourd'hui: 00:00:00");
        dayDurationLabel.setFont(new Font("Arial", Font.PLAIN, 18));

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BorderLayout());
        bottomPanel.add(pauseButton, BorderLayout.CENTER);
        bottomPanel.add(dayDurationLabel, BorderLayout.EAST);

        // Right panel for todo list
        todoTableModel = new MyTodoTableModel();
        todoTable = new JTable(todoTableModel);
        todoTable.setSurrendersFocusOnKeystroke(true);

        // Set custom editor for the status column
        JComboBox<TodoStatus> statusComboBox = new JComboBox<>(TodoStatus.values());
        todoTable.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(statusComboBox));

        JScrollPane todoScrollPane = new JScrollPane(todoTable);

        hideDoneCheckBox = new JCheckBox("Hide done actions");
        hideDoneCheckBox.addActionListener(e -> todoTableModel.setHideDone(hideDoneCheckBox.isSelected()));

        JPanel todoTopPanel = new JPanel(new BorderLayout());
        todoTopPanel.add(hideDoneCheckBox, BorderLayout.WEST);

        JPanel todoPanel = new JPanel(new BorderLayout());
        todoPanel.add(todoTopPanel, BorderLayout.NORTH);
        todoPanel.add(todoScrollPane, BorderLayout.CENTER);

        // Split pane to hold both tables
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, ticketScrollPane, todoPanel);
        splitPane.setDividerLocation(500);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(splitPane, BorderLayout.CENTER);
        getContentPane().add(bottomPanel, BorderLayout.SOUTH);

        // Initialize timers and load state
        currentDate = LocalDate.now();
        dayStartTime = System.currentTimeMillis();

        // Load state from file
        loadState();

        startDayTimer();
        startSaveTimer();

        // Add window listener to save state on exit
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
            // Save the index of the currently running ticket before pausing
            lastRunningTicketIndex = ticketTableModel.currentRunningIndex;
            // Stop timers
            ticketTableModel.pauseAll();
            dayElapsedTime += System.currentTimeMillis() - dayStartTime;
            dayTimer.stop();
        } else {
            pauseButton.setText("Pause");
            // Check if the date has changed
            if (!currentDate.equals(LocalDate.now())) {
                currentDate = LocalDate.now();
                dayElapsedTime = 0;
            }
            dayStartTime = System.currentTimeMillis();
            startDayTimer();
            // Resume timers
            if (lastRunningTicketIndex != -1) {
                ticketTableModel.resumeTicketAt(lastRunningTicketIndex);
            }
        }
    }

    private void startDayTimer() {
        dayTimer = new Timer(1000, e -> updateDayDuration());
        dayTimer.start();
    }

    private void updateDayDuration() {
        long elapsed = dayElapsedTime + (System.currentTimeMillis() - dayStartTime);
        dayDurationLabel.setText("Durée de travail aujourd'hui: " + formatDuration(elapsed));
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
            appState.lastRunningTicketIndex = isPaused ? lastRunningTicketIndex : ticketTableModel.currentRunningIndex;
            appState.tickets = new ArrayList<>();
            for (Ticket ticket : ticketTableModel.tickets) {
                TicketState ts = new TicketState();
                ts.jiraId = ticket.jiraId;
                ts.comment = ticket.comment;
                ts.elapsedTime = ticket.elapsedTime;
                if (!isPaused && ticket.timer.isRunning()) {
                    ts.elapsedTime += System.currentTimeMillis() - ticket.startTime;
                }
                // Save todo list
                ts.todos = new ArrayList<>();
                for (TodoItem todo : ticket.todoList) {
                    TodoState todoState = new TodoState();
                    todoState.status = todo.status.name();
                    todoState.title = todo.title;
                    todoState.comment = todo.comment;
                    ts.todos.add(todoState);
                }
                appState.tickets.add(ts);
            }
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
                // Restore state
                this.dayElapsedTime = appState.dayElapsedTime;
                this.currentDate = LocalDate.parse(appState.currentDate);
                this.lastRunningTicketIndex = appState.lastRunningTicketIndex;
                ticketTableModel.tickets.clear();
                for (TicketState ts : appState.tickets) {
                    Ticket ticket = new Ticket(ts.jiraId, ts.comment, ticketTableModel);
                    ticket.elapsedTime = ts.elapsedTime;
                    // Restore todo list
                    for (TodoState todoState : ts.todos) {
                        TodoItem todo = new TodoItem(TodoStatus.valueOf(todoState.status), todoState.title, todoState.comment);
                        ticket.todoList.add(todo);
                    }
                    ticketTableModel.tickets.add(ticket);
                }
                // Update UI
                ticketTableModel.fireTableDataChanged();
                updateDayDuration();
                if (!currentDate.equals(LocalDate.now())) {
                    dayElapsedTime = 0;
                    currentDate = LocalDate.now();
                }
                if (!isPaused && lastRunningTicketIndex != -1) {
                    ticketTableModel.resumeTicketAt(lastRunningTicketIndex);
                }
                System.out.println("État de l'application chargé depuis le JSON.");
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(TimeTrackerApp::new);
    }

    // Classes for saving state
    static class AppState {
        long dayElapsedTime;
        String currentDate;
        int lastRunningTicketIndex;
        List<TicketState> tickets;
    }

    static class TicketState {
        String jiraId;
        String comment;
        long elapsedTime;
        List<TodoState> todos;
    }

    static class TodoState {
        String status;
        String title;
        String comment;
    }

    // Custom table model for tickets
    class MyTicketTableModel extends AbstractTableModel {
        private String[] columnNames = {"JIRA ID", "Comment", "Durée"};
        private List<Ticket> tickets = new ArrayList<>();
        private int currentRunningIndex = -1;

        public MyTicketTableModel() {
            // Tickets will be loaded from state
        }

        public void addTicket(Ticket ticket) {
            tickets.add(ticket);
            fireTableRowsInserted(tickets.size() - 1, tickets.size() - 1);
        }

        public void pauseAll() {
            if (currentRunningIndex != -1) {
                tickets.get(currentRunningIndex).pause();
                currentRunningIndex = -1;
            }
        }

        public void resumeTicketAt(int index) {
            if (index >= 0 && index < tickets.size()) {
                tickets.get(index).resume();
                currentRunningIndex = index;
            }
        }

        public void startTimerAt(int row) {
            pauseAll();
            resumeTicketAt(row);
        }

        public int indexOfTicket(Ticket ticket) {
            return tickets.indexOf(ticket);
        }

        @Override
        public int getRowCount() {
            // Add one extra row for the empty row at the end
            return tickets.size() + 1;
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex < tickets.size()) {
                Ticket ticket = tickets.get(rowIndex);
                switch (columnIndex) {
                    case 0:
                        return ticket.jiraId;
                    case 1:
                        return ticket.comment;
                    case 2:
                        return formatDuration(ticket.getElapsedTime());
                    default:
                        return null;
                }
            } else {
                // Empty row
                return null;
            }
        }

        @Override
        public String getColumnName(int col) {
            return columnNames[col];
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            // Allow editing of the last empty row and the "Comment" field of existing tickets
            if (row == tickets.size()) {
                return col == 0 || col == 1;
            } else if (col == 1) {
                return true; // Allow editing of comments for existing tickets
            }
            return false;
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (row == tickets.size()) {
                // Editing the empty row
                if (col == 0) {
                    String jiraId = (String) value;
                    if (jiraId != null && !jiraId.trim().isEmpty()) {
                        // Create a new ticket
                        String comment = (String) getValueAt(row, 1);
                        if (comment == null) comment = "";
                        Ticket ticket = new Ticket(jiraId.trim(), comment.trim(), this);
                        addTicket(ticket);
                        fireTableRowsInserted(tickets.size() - 1, tickets.size() - 1);
                    }
                } else if (col == 1) {
                    // Store the comment temporarily
                    fireTableCellUpdated(row, col);
                }
            } else {
                // Editing an existing ticket's comment
                if (col == 1) {
                    tickets.get(row).comment = (String) value;
                    fireTableCellUpdated(row, col);
                }
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

    // Ticket class
    class Ticket {
        String jiraId;
        String comment;
        long startTime = 0;
        long elapsedTime = 0;
        transient Timer timer;
        MyTicketTableModel tableModel;
        List<TodoItem> todoList = new ArrayList<>();

        public Ticket(String jiraId, String comment, MyTicketTableModel tableModel) {
            this.jiraId = jiraId;
            this.comment = comment;
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
            elapsedTime += System.currentTimeMillis() - startTime;
            if (timer != null) {
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
            int index = tableModel.indexOfTicket(this);
            if (index != -1) {
                tableModel.fireTableRowsUpdated(index, index);
            }
        }
    }

    // Enum for Todo Status
    enum TodoStatus {
        TODO,
        IN_PROGRESS,
        BLOCKED,
        DONE
    }

    // Todo item class
    class TodoItem {
        TodoStatus status;
        String title;
        String comment;

        public TodoItem(TodoStatus status, String title, String comment) {
            this.status = status;
            this.title = title;
            this.comment = comment;
        }
    }

    // Table model for todo list
    class MyTodoTableModel extends AbstractTableModel {
        private String[] columnNames = {"Status", "Title", "Comment"};
        private List<TodoItem> todoItems = new ArrayList<>();
        private boolean hideDone = false;

        public void setHideDone(boolean hideDone) {
            this.hideDone = hideDone;
            fireTableDataChanged();
        }

        public void setTodoItems(List<TodoItem> items) {
            this.todoItems = items;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            // Add one extra row for the empty row at the end
            int count = (int) getFilteredItems().size();
            return count + 1;
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        private List<TodoItem> getFilteredItems() {
            List<TodoItem> filtered = new ArrayList<>();
            for (TodoItem item : todoItems) {
                if (!(hideDone && item.status == TodoStatus.DONE)) {
                    filtered.add(item);
                }
            }
            return filtered;
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
                    default:
                        return null;
                }
            } else {
                // Empty row
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
                // Adding new item
                if (col == 1) {
                    String title = (String) value;
                    if (title != null && !title.trim().isEmpty()) {
                        TodoItem newItem = new TodoItem(TodoStatus.TODO, title.trim(), "");
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
    }

    // Handles ticket selection to display its todo list
    class TicketSelectionHandler implements ListSelectionListener {
        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (!e.getValueIsAdjusting() && !isPaused) {
                int selectedRow = ticketTable.getSelectedRow();
                if (selectedRow >= 0 && selectedRow < ticketTableModel.tickets.size()) {
                    ticketTableModel.startTimerAt(selectedRow);
                    Ticket selectedTicket = ticketTableModel.tickets.get(selectedRow);
                    todoTableModel.setTodoItems(selectedTicket.todoList);
                } else {
                    // No valid ticket selected
                    todoTableModel.setTodoItems(new ArrayList<>());
                }
            }
        }
    }
}
