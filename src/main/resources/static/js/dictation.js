(function () {
    var CLIENT_KEY = "dictationClientId";
    var clientId = localStorage.getItem(CLIENT_KEY);
    if (!clientId) {
        clientId = (window.crypto && crypto.randomUUID)
            ? crypto.randomUUID()
            : "c-" + Date.now() + "-" + Math.random().toString(16).slice(2);
        localStorage.setItem(CLIENT_KEY, clientId);
    }

    var catalogEl = document.getElementById("catalog");
    var errorEl = document.getElementById("global-error");
    var selectStatus = document.getElementById("select-status");
    var btnConfirm = document.getElementById("btn-confirm");
    var btnStart = document.getElementById("btn-start");
    var btnPrev = document.getElementById("btn-prev");
    var btnNext = document.getElementById("btn-next");
    var btnSpeak = document.getElementById("btn-speak");
    var btnFinish = document.getElementById("btn-finish");
    var btnBackSelect = document.getElementById("btn-back-select");
    var btnNew = document.getElementById("btn-new");
    var progressEl = document.getElementById("progress");
    var speakStatus = document.getElementById("speak-status");
    var reviewList = document.getElementById("review-list");
    var reviewStatus = document.getElementById("review-status");
    var intervalSelect = document.getElementById("interval-select");

    var practiceWords = [];
    var index = 0;
    var speaking = false;
    var currentAudio = null;

    function showError(message) {
        if (!message) {
            errorEl.classList.add("hidden");
            errorEl.textContent = "";
            return;
        }
        errorEl.textContent = message;
        errorEl.classList.remove("hidden");
    }

    function showView(name) {
        document.getElementById("view-select").classList.toggle("hidden", name !== "select");
        document.getElementById("view-practice").classList.toggle("hidden", name !== "practice");
        document.getElementById("view-review").classList.toggle("hidden", name !== "review");
    }

    function api(path, options) {
        return fetch(path, options).then(function (res) {
            return res.json().then(function (body) {
                if (!res.ok) {
                    throw new Error(body.message || "请求失败");
                }
                return body;
            });
        });
    }

    function groupedCatalog(lists) {
        var groups = [];
        var map = {};
        lists.forEach(function (list) {
            var key = list.unitId;
            if (!map[key]) {
                map[key] = { unitId: list.unitId, unitTitle: list.unitTitle, lists: [] };
                groups.push(map[key]);
            }
            map[key].lists.push(list);
        });
        return groups;
    }

    function renderCatalog(lists) {
        var groups = groupedCatalog(lists);
        catalogEl.innerHTML = "";
        groups.forEach(function (group) {
            var block = document.createElement("section");
            block.className = "unit-block";
            var h2 = document.createElement("h2");
            h2.textContent = group.unitTitle;
            block.appendChild(h2);
            group.lists.forEach(function (list) {
                var head = document.createElement("div");
                head.className = "list-head";
                var title = document.createElement("strong");
                title.textContent = list.listTitle + "（" + list.words.length + "）";
                var toggle = document.createElement("button");
                toggle.type = "button";
                toggle.textContent = "本表全选";
                toggle.addEventListener("click", function () {
                    var boxes = block.querySelectorAll('input[data-list="' + list.listId + '"]');
                    var allOn = true;
                    boxes.forEach(function (box) {
                        if (!box.checked) {
                            allOn = false;
                        }
                    });
                    boxes.forEach(function (box) {
                        box.checked = !allOn;
                    });
                    toggle.textContent = allOn ? "本表全选" : "取消本表";
                    btnStart.classList.add("hidden");
                });
                head.appendChild(title);
                head.appendChild(toggle);
                block.appendChild(head);
                list.words.forEach(function (word) {
                    var row = document.createElement("label");
                    row.className = "word-row";
                    row.innerHTML =
                        '<input type="checkbox" data-id="' + word.id + '" data-list="' + list.listId + '">' +
                        '<span class="en">' + escapeHtml(word.word) + "</span>" +
                        '<span class="ipa">/' + escapeHtml(word.phonetic || "") + "/</span>" +
                        '<span class="zh">' + escapeHtml(word.chinese || "") + "</span>";
                    row.querySelector("input").addEventListener("change", function () {
                        btnStart.classList.add("hidden");
                    });
                    block.appendChild(row);
                });
            });
            catalogEl.appendChild(block);
        });
    }

    function escapeHtml(text) {
        return String(text)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;");
    }

    function selectedIds() {
        var ids = [];
        catalogEl.querySelectorAll("input[data-id]:checked").forEach(function (box) {
            ids.push(box.getAttribute("data-id"));
        });
        return ids;
    }

    function applySavedSelection(selected) {
        var set = {};
        (selected || []).forEach(function (item) {
            set[item.id] = true;
        });
        catalogEl.querySelectorAll("input[data-id]").forEach(function (box) {
            box.checked = !!set[box.getAttribute("data-id")];
        });
        if (selected && selected.length) {
            btnStart.classList.remove("hidden");
            selectStatus.textContent = "已恢复本次范围，共 " + selected.length + " 个单词（12 小时内有效）";
        }
    }

    function playAudioOnce(text) {
        return new Promise(function (resolve) {
            var audio = new Audio("/api/dictation/speak?text=" + encodeURIComponent(text));
            currentAudio = audio;
            audio.preload = "auto";
            var started = false;
            function doPlay() {
                if (started) return;
                started = true;
                setTimeout(function () {
                    audio.play().catch(function () { resolve(); });
                }, 800);
            }
            audio.addEventListener("canplaythrough", doPlay);
            audio.onended = function () { resolve(); };
            audio.onerror = function () { resolve(); };
        });
    }

    function speakThreeTimes(text) {
        speaking = true;
        btnSpeak.disabled = true;
        btnPrev.disabled = true;
        btnNext.disabled = true;
        btnFinish.disabled = true;
        speakStatus.textContent = "正在加载音频…";
        var round = 0;
        var intervalSec = parseInt(intervalSelect.value, 10) || 2;

        function waitWithCountdown(sec) {
            return new Promise(function (resolve) {
                var remaining = sec;
                speakStatus.textContent = "间隔 " + remaining + " 秒…";
                var timer = setInterval(function () {
                    remaining -= 1;
                    if (remaining <= 0) {
                        clearInterval(timer);
                        resolve();
                    } else {
                        speakStatus.textContent = "间隔 " + remaining + " 秒…";
                    }
                }, 1000);
            });
        }

        function next() {
            round += 1;
            speakStatus.textContent = "第 " + round + " 次朗读…";
            return playAudioOnce(text).then(function () {
                if (round >= 3) {
                    speaking = false;
                    btnSpeak.disabled = false;
                    btnPrev.disabled = false;
                    btnNext.disabled = false;
                    btnFinish.disabled = false;
                    currentAudio = null;
                    speakStatus.textContent = "朗读完成";
                    return;
                }
                return waitWithCountdown(intervalSec).then(next);
            });
        }

        stopAudio();
        return next();
    }

    function stopAudio() {
        if (currentAudio) {
            currentAudio.pause();
            currentAudio = null;
        }
    }

    function renderPractice() {
        var total = practiceWords.length;
        progressEl.textContent = "当前第 " + (index + 1) + " 个 / 共 " + total + " 个";
        var first = index === 0;
        var last = index === total - 1;
        btnPrev.classList.toggle("hidden", first);
        btnNext.classList.toggle("hidden", last);
        btnFinish.classList.toggle("hidden", !last);
    }

    function highlight(sentence, word) {
        if (!sentence) {
            return "";
        }
        var escaped = word.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
        var re = new RegExp("(" + escaped + ")", "gi");
        return escapeHtml(sentence).replace(re, '<strong class="word-hl">$1</strong>');
    }

    function renderReview(words) {
        reviewList.innerHTML = "";
        words.forEach(function (item, i) {
            var card = document.createElement("article");
            card.className = "review-card";
            var examples = item.examples || [];
            var e1 = examples[0] || { english: "", chinese: "" };
            var e2 = examples[1] || { english: "", chinese: "" };
            card.innerHTML =
                '<div class="review-head">' +
                "<span>" + (i + 1) + ".</span>" +
                '<span class="en">' + escapeHtml(item.word) + "</span>" +
                '<span class="ipa">/' + escapeHtml(item.phonetic || "") + "/</span>" +
                "<span>" + escapeHtml(item.chinese || "") + "</span>" +
                "</div>" +
                '<div class="example"><div>' + highlight(e1.english, item.word) +
                '</div><div class="zh">' + escapeHtml(e1.chinese || "") + "</div></div>" +
                '<div class="example"><div>' + highlight(e2.english, item.word) +
                '</div><div class="zh">' + escapeHtml(e2.chinese || "") + "</div></div>";
            reviewList.appendChild(card);
        });
    }

    btnConfirm.addEventListener("click", function () {
        showError("");
        var ids = selectedIds();
        if (!ids.length) {
            showError("请至少勾选一个单词");
            return;
        }
        btnConfirm.disabled = true;
        api("/api/dictation/selection", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ clientId: clientId, wordIds: ids })
        }).then(function (body) {
            btnStart.classList.remove("hidden");
            selectStatus.textContent = "已保存 " + body.total + " 个单词，12 小时后失效";
        }).catch(function (err) {
            showError(err.message);
        }).then(function () {
            btnConfirm.disabled = false;
        });
    });

    btnStart.addEventListener("click", function () {
        showError("");
        api("/api/dictation/start", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ clientId: clientId })
        }).then(function (body) {
            practiceWords = body.words || [];
            index = 0;
            showView("practice");
            renderPractice();
        }).catch(function (err) {
            showError(err.message);
        });
    });

    btnPrev.addEventListener("click", function () {
        if (speaking || index <= 0) {
            return;
        }
        index -= 1;
        renderPractice();
        speakStatus.textContent = "";
    });

    btnNext.addEventListener("click", function () {
        if (speaking || index >= practiceWords.length - 1) {
            return;
        }
        index += 1;
        renderPractice();
        speakStatus.textContent = "";
    });

    btnSpeak.addEventListener("click", function () {
        var current = practiceWords[index];
        if (!current || speaking) {
            return;
        }
        speakThreeTimes(current.word);
    });

    btnFinish.addEventListener("click", function () {
        if (speaking) {
            return;
        }
        showError("");
        showView("review");
        reviewStatus.textContent = "正在加载例句与翻译，请稍候…";
        reviewList.innerHTML = "";
        api("/api/dictation/review?clientId=" + encodeURIComponent(clientId)).then(function (body) {
            reviewStatus.textContent = "按听写时的乱序展示，共 " + body.total + " 个单词";
            renderReview(body.words || []);
        }).catch(function (err) {
            showError(err.message);
            reviewStatus.textContent = "";
        });
    });

    btnBackSelect.addEventListener("click", function () {
        stopAudio();
        speaking = false;
        btnSpeak.disabled = false;
        btnPrev.disabled = false;
        btnNext.disabled = false;
        btnFinish.disabled = false;
        showView("select");
    });

    btnNew.addEventListener("click", function () {
        showView("select");
    });

    showView("select");
    api("/api/dictation/catalog").then(function (lists) {
        renderCatalog(lists);
        return api("/api/dictation/selection?clientId=" + encodeURIComponent(clientId));
    }).then(function (body) {
        applySavedSelection(body.selected);
    }).catch(function (err) {
        showError(err.message);
    });
})();
