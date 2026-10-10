(function () {
    var W_FILL = "#fffef2";
    var W_STROKE = "#4a3520";
    var B_FILL = "#1c1c1c";
    var B_STROKE = "#000";

    function pc(body, extra) {
        return "data:image/svg+xml," + encodeURIComponent(
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 45 45">'
            + body + (extra || "") + "</svg>"
        );
    }

    function pieceSet(body, collar, extras) {
        return function (fill, stroke) {
            return pc(
                '<g fill="' + fill + '" stroke="' + stroke + '" stroke-width="1.5" stroke-linejoin="round">'
                + body + collar + "</g>" + (extras ? extras(fill, stroke) : ""),
                ""
            );
        };
    }

    var kingBody =
        '<path d="M22.5 11.63V6M20 8h5" stroke-linejoin="miter" stroke-width="1.5"/>'
        + '<path d="M22.5 25s4.5-7.5 3-10.5c0 0-1-2.5-3-2.5s-3 2.5-3 2.5c-1.5 3 3 10.5 3 10.5" '
        + 'fill="inherit" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"/>'
        + '<path d="M11.5 37c5.5 3.5 15.5 3.5 21 0v-7s9-4.5 6-10.5c-4-6.5-13.5-3.5-16 4V27v-3.5c-3.5-7.5-13-10.5-16-4-3 6 5 10 5 10V37z"/>'
        + '<path d="M11.5 30c5.5-3 15.5-3 21 0" fill="none"/>'
        + '<path d="M11.5 33.5c5.5-3 15.5-3 21 0" fill="none"/>';

    var queenBody =
        '<circle cx="6" cy="12" r="2.75"/>'
        + '<circle cx="14" cy="9" r="2.75"/>'
        + '<circle cx="22.5" cy="8" r="2.75"/>'
        + '<circle cx="31" cy="9" r="2.75"/>'
        + '<circle cx="39" cy="12" r="2.75"/>'
        + '<path d="M9 26c8.5-1.5 21-1.5 27 0l2.5-12.5L31 25l-3.5-7-5 6.5-5-6.5-3.5 7-7.5-13L9 26z" stroke-linecap="butt"/>'
        + '<path d="M9 26c0 2 1.5 2 2.5 4 1 1.5 1 1 .5 3.5-1.5 1-1.5 2.5-1.5 2.5-1.5 1.5.5 2.5.5 2.5 6.5 1 16.5 1 23 0 0 0 1.5-1 0-2.5 0 0 .5-1.5-1-2.5-.5-2.5-.5-2 .5-3.5 1-2 2.5-2 2.5-4-8.5-1.5-18.5-1.5-27 0z" stroke-linecap="butt"/>';

    var rookBody =
        '<path d="M9 39h27v-3H9v3zM12 36v-4h21v4H12zM11 14V9h4v2h5V9h5v2h5V9h4v5" stroke-linecap="round"/>'
        + '<path d="M34 14l-3 3H14l-3-3"/>'
        + '<path d="M15 17v7h15v-7" stroke-linecap="butt" stroke="none"/>'
        + '<path d="M14 29.5v-13h17v13H14z" stroke-linecap="butt"/>'
        + '<path d="M14 29.5H31M14 25.5H31M14 21.5H31" fill="none" stroke-linejoin="miter"/>';

    var bishopBody =
        '<g stroke-linecap="butt">'
        + '<path d="M9 36c3.39-.97 10.11.43 13.5-2 3.39 2.43 10.11 1.03 13.5 2 0 0 1.65.54 3 2-.68.97-1.65.99-3 .5-3.39-.97-10.11.46-13.5-1-3.39 1.46-10.11.03-13.5 1-1.354.49-2.323.47-3-.5 1.354-1.94 3-2 3-2z"/>'
        + '<path d="M15 32c2.5 2.5 12.5 2.5 15 0 .5-1.5 0-2 0-2 0-2.5-2.5-4-2.5-4 5.5-1.5 6-11.5-5-15.5-11 4-10.5 14-5 15.5 0 0-2.5 1.5-2.5 4 0 0-.5.5 0 2z"/>'
        + '<path d="M25 8a2.5 2.5 0 1 1-5 0 2.5 2.5 0 1 1 5 0z"/>'
        + '</g>'
        + '<path d="M17.5 26h10M15 30h15" stroke-linejoin="miter" fill="none"/>';

    var knightBody =
        '<g stroke-linecap="round" stroke-linejoin="round">'
        + '<path d="M 22,10 C 32.5,11 38.5,18 38,39 L 15,39 C 15,30 25,32.5 23,21"/>'
        + '<path d="M 24,18 C 24.38,20.91 18.45,25.37 16,27 C 16,27 16.5,27.5 16,28 C 14.5,29.5 10,29 10,29 C 10,29 9,27 11,25 C 13,23 15.5,22 16.5,19.5 C 17.5,17 18,15 19,14 C 20,13 22.5,12.5 24,12"/>'
        + '<path d="M 22,10 C 22,10 19.5,12 18,12 C 15.5,12 14,10 14,10" fill="none"/>'
        + '</g>'
        + '<path d="M 13.5,26 C 14,26 14.5,25.5 14,25 C 13.5,24.5 13,25 13.5,26" fill="black" stroke="black"/>';

    var pawnBody =
        '<path d="M22.5 9c-2.21 0-4 1.79-4 4 0 .89.29 1.71.78 2.38C17.33 16.5 16 18.59 16 21c0 2.03.94 3.84 2.41 5.03C15.5 27.5 14 30 14 33h17c0-3-1.5-5.5-4.41-6.97C28.06 24.84 29 23.03 29 21c0-2.41-1.33-4.5-3.28-5.62.49-.67.78-1.49.78-2.38 0-2.21-1.79-4-4-4z"/>';

    function blackGlow(body) {
        return '<g fill="none" stroke="rgba(180,170,150,0.35)" stroke-width="1.2" stroke-linejoin="round">'
            + body + "</g>";
    }

    function makeCollar(fill, stroke) {
        return '<path d="M11.5 30c5.5-3 15.5-3 21 0" fill="none" stroke="' + stroke + '" stroke-width="1.5"/>'
            + '<path d="M11.5 33.5c5.5-3 15.5-3 21 0" fill="none" stroke="' + stroke + '" stroke-width="1.5"/>';
    }

    var PIECES = {
        wK: pieceSet(kingBody, "", null)(W_FILL, W_STROKE),
        wQ: pieceSet(queenBody, "", null)(W_FILL, W_STROKE),
        wR: pieceSet(rookBody, "", null)(W_FILL, W_STROKE),
        wB: pieceSet(bishopBody, "", null)(W_FILL, W_STROKE),
        wN: pieceSet(knightBody, "", null)(W_FILL, W_STROKE),
        wP: pieceSet(pawnBody, "", null)(W_FILL, W_STROKE),
        bK: pieceSet(kingBody, "", blackGlow)(B_FILL, B_STROKE),
        bQ: pieceSet(queenBody, "", blackGlow)(B_FILL, B_STROKE),
        bR: pieceSet(rookBody, "", blackGlow)(B_FILL, B_STROKE),
        bB: pieceSet(bishopBody, "", blackGlow)(B_FILL, B_STROKE),
        bN: pieceSet(knightBody, "", blackGlow)(B_FILL, B_STROKE),
        bP: pieceSet(pawnBody, "", blackGlow)(B_FILL, B_STROKE)
    };
    var ENGINE_ELO = 2150;
    var MOVE_TIME_MS = 1000;

    var chess = new Chess();
    var playerColor = "w";
    var selected = null;
    var lastMove = null;
    var engineReady = false;
    var thinking = false;
    var gameOver = false;
    var pendingPromo = null;
    var boardEl = document.getElementById("board");
    var movesEl = document.getElementById("moves");
    var statusEl = document.getElementById("status");
    var promoEl = document.getElementById("promo");
    var worker = null;

    function sqName(file, rank) {
        return "abcdefgh".charAt(file) + (rank + 1);
    }

    function filesRanks() {
        var files = [0, 1, 2, 3, 4, 5, 6, 7];
        var ranks = [7, 6, 5, 4, 3, 2, 1, 0];
        if (playerColor === "b") {
            files.reverse();
            ranks.reverse();
        }
        return { files: files, ranks: ranks };
    }

    function kingInCheckSquare() {
        if (!chess.in_check()) {
            return null;
        }
        var board = chess.board();
        var turn = chess.turn();
        for (var r = 0; r < 8; r++) {
            for (var f = 0; f < 8; f++) {
                var p = board[r][f];
                if (p && p.type === "k" && p.color === turn) {
                    return sqName(f, 7 - r);
                }
            }
        }
        return null;
    }

    function legalTargets(from) {
        return chess.moves({ square: from, verbose: true }).map(function (m) {
            return m.to;
        });
    }

    function renderBoard() {
        boardEl.innerHTML = "";
        var orient = filesRanks();
        var targets = selected ? legalTargets(selected) : [];
        var checkSq = kingInCheckSquare();
        orient.ranks.forEach(function (rank) {
            orient.files.forEach(function (file) {
                var name = sqName(file, rank);
                var light = (file + rank) % 2 === 1;
                var sq = document.createElement("div");
                sq.className = "sq " + (light ? "light" : "dark");
                sq.dataset.square = name;
                if (lastMove && (name === lastMove.from || name === lastMove.to)) {
                    sq.classList.add("last");
                }
                if (selected === name) {
                    sq.classList.add("selected");
                }
                if (checkSq === name) {
                    sq.classList.add("check");
                }
                var piece = chess.get(name);
                if (piece) {
                    var key = piece.color + piece.type.toUpperCase();
                    var img = document.createElement("img");
                    img.className = "piece " + (piece.color === "w" ? "piece-w" : "piece-b");
                    img.src = PIECES[key];
                    img.draggable = false;
                    sq.appendChild(img);
                }
                if (targets.indexOf(name) >= 0) {
                    if (piece) {
                        var ring = document.createElement("span");
                        ring.className = "capture-ring";
                        sq.appendChild(ring);
                    } else {
                        var dot = document.createElement("span");
                        dot.className = "dot";
                        sq.appendChild(dot);
                    }
                }
                sq.addEventListener("click", function () {
                    onSquareClick(name);
                });
                boardEl.appendChild(sq);
            });
        });
        renderMoves();
        renderStatus();
    }

    function renderMoves() {
        var history = chess.history({ verbose: true });
        movesEl.innerHTML = "";
        for (var i = 0; i < history.length; i += 2) {
            var li = document.createElement("li");
            var num = document.createElement("span");
            num.className = "num";
            num.textContent = (i / 2 + 1) + ".";
            var white = document.createElement("span");
            white.textContent = history[i].san;
            var black = document.createElement("span");
            black.textContent = history[i + 1] ? history[i + 1].san : "";
            if (i === history.length - 1) {
                white.className = "current";
            }
            if (history[i + 1] && i + 1 === history.length - 1) {
                black.className = "current";
            }
            li.appendChild(num);
            li.appendChild(white);
            li.appendChild(black);
            movesEl.appendChild(li);
        }
        movesEl.scrollTop = movesEl.scrollHeight;
    }

    function renderStatus() {
        if (!engineReady) {
            statusEl.textContent = "正在加载引擎…";
            return;
        }
        if (thinking) {
            statusEl.textContent = "电脑思考中…";
            return;
        }
        if (chess.in_checkmate()) {
            statusEl.textContent = chess.turn() === "w" ? "将死，黑胜" : "将死，白胜";
            return;
        }
        if (chess.in_stalemate()) {
            statusEl.textContent = "逼和";
            return;
        }
        if (chess.in_threefold_repetition()) {
            statusEl.textContent = "三次重复，和棋";
            return;
        }
        if (chess.insufficient_material()) {
            statusEl.textContent = "子力不足，和棋";
            return;
        }
        if (chess.in_draw()) {
            statusEl.textContent = "和棋";
            return;
        }
        if (gameOver) {
            statusEl.textContent = "对局已结束";
            return;
        }
        var side = chess.turn() === "w" ? "白方" : "黑方";
        statusEl.textContent = (chess.in_check() ? "将军 · " : "") + side + "走棋";
    }

    function isPlayersTurn() {
        return !gameOver && !thinking && chess.turn() === playerColor;
    }

    function onSquareClick(name) {
        if (!isPlayersTurn()) {
            return;
        }
        var piece = chess.get(name);
        if (selected && selected !== name) {
            var move = tryMove(selected, name);
            if (move) {
                return;
            }
        }
        if (piece && piece.color === playerColor) {
            selected = name;
            renderBoard();
        } else {
            selected = null;
            renderBoard();
        }
    }

    function needsPromotion(from, to) {
        var piece = chess.get(from);
        if (!piece || piece.type !== "p") {
            return false;
        }
        var rank = to.charAt(1);
        return (piece.color === "w" && rank === "8") || (piece.color === "b" && rank === "1");
    }

    function showPromo(color) {
        promoEl.querySelectorAll(".promo-choices button").forEach(function (btn) {
            var p = btn.getAttribute("data-piece").toUpperCase();
            var img = btn.querySelector("img");
            if (img) {
                img.src = PIECES[color + p];
            }
        });
    }

    function tryMove(from, to, promotion) {
        if (needsPromotion(from, to) && !promotion) {
            pendingPromo = { from: from, to: to };
            showPromo(playerColor);
            promoEl.classList.remove("hidden");
            return true;
        }
        var move = chess.move({
            from: from,
            to: to,
            promotion: promotion || "q"
        });
        if (!move) {
            return false;
        }
        selected = null;
        lastMove = { from: move.from, to: move.to };
        afterHumanMove();
        return true;
    }

    function afterHumanMove() {
        if (chess.game_over()) {
            gameOver = true;
            renderBoard();
            return;
        }
        renderBoard();
        askEngine();
    }

    function applyEngineMove(uci) {
        if (!uci || uci === "(none)") {
            gameOver = true;
            renderBoard();
            return;
        }
        var from = uci.substring(0, 2);
        var to = uci.substring(2, 4);
        var promo = uci.length > 4 ? uci.charAt(4) : undefined;
        var move = chess.move({ from: from, to: to, promotion: promo });
        if (move) {
            lastMove = { from: move.from, to: move.to };
        }
        thinking = false;
        if (chess.game_over()) {
            gameOver = true;
        }
        renderBoard();
    }

    function askEngine() {
        if (!engineReady || gameOver || chess.game_over()) {
            return;
        }
        thinking = true;
        renderStatus();
        worker.postMessage("position fen " + chess.fen());
        worker.postMessage("go movetime " + MOVE_TIME_MS);
    }

    function startGame(color) {
        playerColor = color;
        chess.reset();
        selected = null;
        lastMove = null;
        gameOver = false;
        thinking = false;
        pendingPromo = null;
        promoEl.classList.add("hidden");
        renderBoard();
        if (engineReady && playerColor === "b") {
            askEngine();
        }
    }

    function resign() {
        if (gameOver) {
            return;
        }
        gameOver = true;
        thinking = false;
        renderBoard();
        statusEl.textContent = playerColor === "w" ? "你认输，黑胜" : "你认输，白胜";
    }

    function initEngine() {
        worker = new Worker("/engine/stockfish-nnue-16-single.js");
        worker.onmessage = function (event) {
            var raw = event.data;
            if (raw && typeof raw === "object") {
                raw = raw.data || raw.text || "";
            }
            if (typeof raw !== "string") {
                return;
            }
            raw.split("\n").forEach(function (line) {
                line = line.trim();
                if (!line) {
                    return;
                }
                if (line === "uciok") {
                    worker.postMessage("setoption name UCI_LimitStrength value true");
                    worker.postMessage("setoption name UCI_Elo value " + ENGINE_ELO);
                    worker.postMessage("setoption name Hash value 32");
                    worker.postMessage("isready");
                } else if (line === "readyok") {
                    engineReady = true;
                    renderStatus();
                    if (playerColor === "b" && chess.history().length === 0) {
                        askEngine();
                    }
                } else if (line.indexOf("bestmove") === 0) {
                    var parts = line.split(" ");
                    applyEngineMove(parts[1]);
                }
            });
        };
        worker.onerror = function () {
            statusEl.textContent = "引擎加载失败，请刷新页面重试";
        };
        worker.postMessage("uci");
    }

    document.getElementById("btn-white").addEventListener("click", function () {
        startGame("w");
    });
    document.getElementById("btn-black").addEventListener("click", function () {
        startGame("b");
    });
    document.getElementById("btn-resign").addEventListener("click", function () {
        resign();
    });
    promoEl.querySelectorAll("button").forEach(function (btn) {
        btn.addEventListener("click", function () {
            if (!pendingPromo) {
                return;
            }
            var from = pendingPromo.from;
            var to = pendingPromo.to;
            pendingPromo = null;
            promoEl.classList.add("hidden");
            tryMove(from, to, btn.getAttribute("data-piece"));
        });
    });

    startGame("w");
    initEngine();
})();
