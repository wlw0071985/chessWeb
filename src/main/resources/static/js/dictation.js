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
    var inputArea = document.getElementById("input-area");
    var btnHintPhonetic = document.getElementById("btn-hint-phonetic");
    var btnHintChinese = document.getElementById("btn-hint-chinese");
    var hintPhonetic = document.getElementById("hint-phonetic");
    var hintChinese = document.getElementById("hint-chinese");

    var practiceWords = [];
    var index = 0;
    var speaking = false;
    var currentAudio = null;
    var globalCompleting = false;

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
                var headLeft = document.createElement("div");
                headLeft.style.display = "flex";
                headLeft.style.alignItems = "center";
                headLeft.style.gap = "10px";
                var title = document.createElement("strong");
                title.textContent = list.listTitle + "（" + list.words.length + "）";
                var toggle = document.createElement("button");
                toggle.type = "button";
                toggle.textContent = "本表全选";
                toggle.addEventListener("click", function () {
                    if (globalCompleting) {
                        showError("有单词正在补全中，请等待完成后再操作");
                        return;
                    }
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
                headLeft.appendChild(title);
                head.appendChild(headLeft);
                head.appendChild(toggle);
                block.appendChild(head);
                list.words.forEach(function (word) {
                    var row = document.createElement("div");
                    row.className = "word-row";
                    row.setAttribute("data-word-id", word.id);
                    var checkbox = document.createElement("input");
                    checkbox.type = "checkbox";
                    checkbox.setAttribute("data-id", word.id);
                    checkbox.setAttribute("data-list", list.listId);
                    checkbox.addEventListener("change", function () {
                        if (globalCompleting) {
                            checkbox.checked = false;
                            showError("有单词正在补全中，请等待完成后再选择");
                            return;
                        }
                        btnStart.classList.add("hidden");
                    });
                    var enSpan = document.createElement("span");
                    enSpan.className = "en";
                    enSpan.textContent = word.word;
                    var ipaSpan = document.createElement("span");
                    ipaSpan.className = "ipa";
                    ipaSpan.textContent = "/" + (word.phonetic || "") + "/";
                    var zhSpan = document.createElement("span");
                    zhSpan.className = "zh";
                    zhSpan.textContent = word.chinese || "";
                    var completeBtn = document.createElement("button");
                    completeBtn.type = "button";
                    completeBtn.className = "btn-complete";
                    completeBtn.textContent = "补全";
                    completeBtn.addEventListener("click", (function (wordItem, btn, ipaEl, zhEl) {
                        return function () {
                            if (globalCompleting) {
                                return;
                            }
                            globalCompleting = true;
                            catalogEl.querySelectorAll("input[data-id]").forEach(function (cb) {
                                cb.disabled = true;
                            });
                            catalogEl.querySelectorAll(".btn-complete:not(.done)").forEach(function (b) {
                                b.disabled = true;
                            });
                            btn.disabled = true;
                            btn.textContent = "补全中…";
                            api("/api/dictation/complete-word", {
                                method: "POST",
                                headers: { "Content-Type": "application/json" },
                                body: JSON.stringify({ id: wordItem.id, word: wordItem.word })
                            }).then(function (res) {
                                if (!res.ok) throw new Error(res.message || "补全失败");
                                if (res.phonetic) ipaEl.textContent = "/" + res.phonetic + "/";
                                if (res.chinese) zhEl.textContent = res.chinese;
                                btn.textContent = "已补全";
                                btn.classList.add("done");
                            }).catch(function (err) {
                                btn.textContent = "补全";
                                showError(err.message);
                            }).finally(function () {
                                globalCompleting = false;
                                reenableCheckboxIfReady();
                            });
                        };
                    })(word, completeBtn, ipaSpan, zhSpan));
                    row.appendChild(checkbox);
                    row.appendChild(enSpan);
                    row.appendChild(ipaSpan);
                    row.appendChild(zhSpan);
                    row.appendChild(completeBtn);
                    block.appendChild(row);
                });
            });
            catalogEl.appendChild(block);
        });
    }

    function reenableCheckboxIfReady() {
        var hasAnyCompleting = catalogEl.querySelector(".btn-complete:not(.done):not(:disabled)");
        if (!hasAnyCompleting) {
            catalogEl.querySelectorAll("input[data-id]").forEach(function (cb) {
                cb.disabled = false;
            });
        } else {
            catalogEl.querySelectorAll(".btn-complete:not(.done)").forEach(function (btn) {
                btn.disabled = false;
            });
        }
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
                }, 1200);
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
        hintPhonetic.style.display = "none";
        hintPhonetic.textContent = "";
        hintChinese.style.display = "none";
        hintChinese.textContent = "";
        btnHintPhonetic.textContent = "显示音标";
        btnHintPhonetic.classList.remove("active");
        btnHintChinese.textContent = "显示中文";
        btnHintChinese.classList.remove("active");
    }

    function renderInputArea() {
        inputArea.innerHTML = "";
        var current = practiceWords[index];
        if (!current) return;
        var words = current.word.split(/\s+/);
        words.forEach(function (w, i) {
            var input = document.createElement("input");
            input.type = "text";
            input.className = "word-input";
            input.placeholder = "第 " + (i + 1) + " 个单词";
            input.setAttribute("data-index", i);
            input.addEventListener("keydown", function (e) {
                if (e.key === "Enter") {
                    e.preventDefault();
                    if (index === practiceWords.length - 1) {
                        btnFinish.click();
                    } else {
                        btnNext.click();
                    }
                }
            });
            inputArea.appendChild(input);
        });
    }

    function getAnswers() {
        var answers = [];
        inputArea.querySelectorAll(".word-input").forEach(function (input) {
            answers.push(input.value.trim());
        });
        return answers;
    }

    function saveCurrentAnswer() {
        try {
            var answers = getAnswers();
            return api("/api/dictation/answer", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ clientId: clientId, index: index, answers: answers })
            }).catch(function (err) {
                console.warn("save answer failed:", err.message);
            });
        } catch (e) {
            console.warn("save answer error:", e.message);
            return Promise.resolve();
        }
    }

    function loadAnswer(idx) {
        return api("/api/dictation/answer?clientId=" + encodeURIComponent(clientId) + "&index=" + idx)
            .then(function (parts) {
                var inputs = inputArea.querySelectorAll(".word-input");
                parts.forEach(function (val, i) {
                    if (inputs[i]) {
                        inputs[i].value = val;
                    }
                });
            }).catch(function () {});
    }

    function toggleHint(type) {
        var current = practiceWords[index];
        if (!current) return;
        if (type === "phonetic") {
            if (hintPhonetic.style.display === "none") {
                hintPhonetic.textContent = "/" + (current.phonetic || "") + "/";
                hintPhonetic.style.display = "block";
                btnHintPhonetic.textContent = "隐藏音标";
                btnHintPhonetic.classList.add("active");
            } else {
                hintPhonetic.style.display = "none";
                hintPhonetic.textContent = "";
                btnHintPhonetic.textContent = "显示音标";
                btnHintPhonetic.classList.remove("active");
            }
        } else {
            if (hintChinese.style.display === "none") {
                hintChinese.textContent = current.chinese || "";
                hintChinese.style.display = "block";
                btnHintChinese.textContent = "隐藏中文";
                btnHintChinese.classList.add("active");
            } else {
                hintChinese.style.display = "none";
                hintChinese.textContent = "";
                btnHintChinese.textContent = "显示中文";
                btnHintChinese.classList.remove("active");
            }
        }
    }

    function highlight(sentence, word) {
        if (!sentence) {
            return "";
        }
        var escaped = word.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
        var re = new RegExp("(" + escaped + ")", "gi");
        return escapeHtml(sentence).replace(re, '<strong class="word-hl">$1</strong>');
    }

    function buildAnswerHtml(correctWord, userAnswer) {
        if (!userAnswer) {
            return '<span class="answer-empty">（未填写）</span> <span class="answer-correct">→ ' + escapeHtml(correctWord) + '</span>';
        }
        var correctParts = correctWord.split(/\s+/);
        var userParts = userAnswer.split(/\s+/).filter(function (p) { return p !== ""; });
        var maxLen = Math.max(correctParts.length, userParts.length);
        var html = "";
        for (var i = 0; i < maxLen; i++) {
            var cp = correctParts[i] || "";
            var up = userParts[i] || "";
            if (cp && up.toLowerCase() === cp.toLowerCase()) {
                html += '<span class="answer-correct">' + escapeHtml(up) + '</span> ';
            } else if (cp && up) {
                html += '<span class="answer-wrong">' + escapeHtml(up) + '</span> ';
            } else if (cp && !up) {
                html += '<span class="answer-wrong">∅</span> ';
            } else if (!cp && up) {
                html += '<span class="answer-wrong">' + escapeHtml(up) + '</span> ';
            }
        }
        html += ' <span class="answer-correct">→ ' + escapeHtml(correctWord) + '</span>';
        return html;
    }

    function renderReview(words) {
        reviewList.innerHTML = "";
        words.forEach(function (item, i) {
            var card = document.createElement("article");
            card.className = "review-card";

            var headDiv = document.createElement("div");
            headDiv.className = "review-head";
            headDiv.innerHTML =
                "<span>" + (i + 1) + ".</span>" +
                '<span class="en">' + escapeHtml(item.word) + "</span>" +
                '<span class="ipa">/' + escapeHtml(item.phonetic || "") + "/</span>" +
                "<span>" + escapeHtml(item.chinese || "") + "</span>";
            card.appendChild(headDiv);

            var answerDiv = document.createElement("div");
            answerDiv.className = "answer-row";
            answerDiv.innerHTML =
                '<span class="answer-label">你的答案：</span>' +
                buildAnswerHtml(item.word, item.answer || "");
            card.appendChild(answerDiv);

            var toggleBtn = document.createElement("button");
            toggleBtn.type = "button";
            toggleBtn.className = "example-toggle";
            toggleBtn.textContent = "显示例句";
            toggleBtn.setAttribute("data-index", i);
            toggleBtn.setAttribute("data-loaded", "false");
            card.appendChild(toggleBtn);

            var exampleDiv = document.createElement("div");
            exampleDiv.className = "example-section hidden";
            exampleDiv.setAttribute("data-index", i);
            card.appendChild(exampleDiv);

            toggleBtn.addEventListener("click", (function (btn, section, wordItem, idx) {
                return function () {
                    if (section.classList.contains("hidden")) {
                        section.classList.remove("hidden");
                        btn.textContent = "隐藏例句";
                        if (btn.getAttribute("data-loaded") !== "true") {
                            section.innerHTML = '<p class="example-loading">正在加载例句…</p>';
                            api("/api/dictation/examples?clientId=" + encodeURIComponent(clientId) + "&index=" + idx)
                                .then(function (examples) {
                                    section.innerHTML = "";
                                    (examples || []).forEach(function (ex) {
                                        var div = document.createElement("div");
                                        div.className = "example";
                                        div.innerHTML =
                                            "<div>" + highlight(ex.english, wordItem.word) + "</div>" +
                                            '<div class="zh">' + escapeHtml(ex.chinese || "") + "</div>";
                                        section.appendChild(div);
                                    });
                                    if (!examples || examples.length === 0) {
                                        section.innerHTML = '<p class="hint">暂无例句</p>';
                                    }
                                    btn.setAttribute("data-loaded", "true");
                                }).catch(function (err) {
                                    section.innerHTML = '<p class="hint">加载失败：' + escapeHtml(err.message) + '</p>';
                                });
                        }
                    } else {
                        section.classList.add("hidden");
                        btn.textContent = "显示例句";
                    }
                };
            })(toggleBtn, exampleDiv, item, i));

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
            renderInputArea();
        }).catch(function (err) {
            showError(err.message);
        });
    });

    btnPrev.addEventListener("click", function () {
        if (speaking || index <= 0) {
            return;
        }
        saveCurrentAnswer();
        index -= 1;
        renderPractice();
        renderInputArea();
        loadAnswer(index);
        speakStatus.textContent = "";
    });

    btnNext.addEventListener("click", function () {
        if (speaking || index >= practiceWords.length - 1) {
            return;
        }
        saveCurrentAnswer();
        index += 1;
        renderPractice();
        renderInputArea();
        loadAnswer(index);
        speakStatus.textContent = "";
    });

    btnSpeak.addEventListener("click", function () {
        var current = practiceWords[index];
        if (!current || speaking) {
            return;
        }
        speakThreeTimes(current.word);
    });

    btnHintPhonetic.addEventListener("click", function () {
        toggleHint("phonetic");
    });

    btnHintChinese.addEventListener("click", function () {
        toggleHint("chinese");
    });

    btnFinish.addEventListener("click", function () {
        if (speaking) {
            return;
        }
        showError("");
        btnFinish.disabled = true;
        saveCurrentAnswer().then(function () {
            showView("review");
            reviewStatus.textContent = "正在加载听写结果，请稍候…";
            reviewList.innerHTML = "";
            return api("/api/dictation/review?clientId=" + encodeURIComponent(clientId));
        }).then(function (body) {
            reviewStatus.textContent = "按听写时的乱序展示，共 " + body.total + " 个单词";
            renderReview(body.words || []);
        }).catch(function (err) {
            showError(err.message);
            reviewStatus.textContent = "";
        }).then(function () {
            btnFinish.disabled = false;
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
