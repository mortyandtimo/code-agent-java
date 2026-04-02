package com.silvershuo.codeagent.interfaces.cli;

import java.util.Arrays;
import java.util.Scanner;

/**
 * 终端版推箱子小游戏。
 * 运行方式：
 * java -cp target/classes com.silvershuo.codeagent.interfaces.cli.SokobanGame
 */
public class SokobanGame {

    private static final char WALL = '#';
    private static final char FLOOR = ' ';
    private static final char TARGET = '.';
    private static final char BOX = '$';
    private static final char BOX_ON_TARGET = '*';
    private static final char PLAYER = '@';
    private static final char PLAYER_ON_TARGET = '+';

    private static final String[] LEVEL = {
            "##########",
            "#   .    #",
            "#   $    #",
            "#   $    #",
            "#  .$.   #",
            "#   @    #",
            "#        #",
            "##########"
    };

    private final char[][] board;
    private int playerRow;
    private int playerCol;
    private int steps;

    public SokobanGame() {
        this.board = new char[LEVEL.length][];
        for (int i = 0; i < LEVEL.length; i++) {
            board[i] = LEVEL[i].toCharArray();
            for (int j = 0; j < board[i].length; j++) {
                if (board[i][j] == PLAYER || board[i][j] == PLAYER_ON_TARGET) {
                    playerRow = i;
                    playerCol = j;
                }
            }
        }
    }

    public static void main(String[] args) {
        SokobanGame game = new SokobanGame();
        game.run();
    }

    private void run() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== 推箱子小游戏 ===");
        System.out.println("操作：W/A/S/D 移动，R 重开，Q 退出");

        while (true) {
            printBoard();
            if (isWin()) {
                System.out.println("恭喜你通关！总步数：" + steps);
                System.out.println("输入 R 可重开，输入 Q 退出。");
            }

            System.out.print("请输入指令: ");
            String input = scanner.nextLine().trim().toUpperCase();
            if (input.isEmpty()) {
                continue;
            }

            char command = input.charAt(0);
            if (command == 'Q') {
                System.out.println("游戏结束，再见！");
                break;
            }
            if (command == 'R') {
                reset();
                continue;
            }
            if (isWin()) {
                System.out.println("已通关，请输入 R 重开或 Q 退出。");
                continue;
            }

            switch (command) {
                case 'W':
                    move(-1, 0);
                    break;
                case 'S':
                    move(1, 0);
                    break;
                case 'A':
                    move(0, -1);
                    break;
                case 'D':
                    move(0, 1);
                    break;
                default:
                    System.out.println("无效指令，请使用 W/A/S/D/R/Q。");
            }
        }
    }

    private void reset() {
        for (int i = 0; i < LEVEL.length; i++) {
            board[i] = Arrays.copyOf(LEVEL[i].toCharArray(), LEVEL[i].length());
            for (int j = 0; j < board[i].length; j++) {
                if (board[i][j] == PLAYER || board[i][j] == PLAYER_ON_TARGET) {
                    playerRow = i;
                    playerCol = j;
                }
            }
        }
        steps = 0;
        System.out.println("已重开当前关卡。");
    }

    private void printBoard() {
        System.out.println();
        System.out.println("步数: " + steps);
        for (char[] row : board) {
            System.out.println(new String(row));
        }
        System.out.println();
    }

    private void move(int dRow, int dCol) {
        int nextRow = playerRow + dRow;
        int nextCol = playerCol + dCol;
        char nextCell = board[nextRow][nextCol];

        if (nextCell == WALL) {
            return;
        }

        if (nextCell == BOX || nextCell == BOX_ON_TARGET) {
            int beyondRow = nextRow + dRow;
            int beyondCol = nextCol + dCol;
            char beyondCell = board[beyondRow][beyondCol];
            if (beyondCell == WALL || beyondCell == BOX || beyondCell == BOX_ON_TARGET) {
                return;
            }
            moveBox(nextRow, nextCol, beyondRow, beyondCol);
        }

        movePlayer(nextRow, nextCol);
        steps++;
    }

    private void moveBox(int fromRow, int fromCol, int toRow, int toCol) {
        board[toRow][toCol] = board[toRow][toCol] == TARGET ? BOX_ON_TARGET : BOX;
        board[fromRow][fromCol] = board[fromRow][fromCol] == BOX_ON_TARGET ? TARGET : FLOOR;
    }

    private void movePlayer(int toRow, int toCol) {
        board[playerRow][playerCol] = board[playerRow][playerCol] == PLAYER_ON_TARGET ? TARGET : FLOOR;
        board[toRow][toCol] = board[toRow][toCol] == TARGET ? PLAYER_ON_TARGET : PLAYER;
        playerRow = toRow;
        playerCol = toCol;
    }

    private boolean isWin() {
        for (char[] row : board) {
            for (char cell : row) {
                if (cell == BOX) {
                    return false;
                }
            }
        }
        return true;
    }
}
