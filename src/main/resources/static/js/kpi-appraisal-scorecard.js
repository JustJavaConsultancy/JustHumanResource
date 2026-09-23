const appraisalId = window.kpiAppraisalScorecardData?.appraisalId;

        async function generateLines(id) {
            const response = await fetch(`/api/appraisals/${id}/lines/generate`, { method: 'POST' });
            if (!response.ok) {
                showAlert(await response.text(), false);
                return;
            }
            showAlert('Scorecard lines generated. Reloading...', true);
            setTimeout(() => window.location.reload(), 800);
        }

        async function saveSelfLine(lineId) {
            await saveLine(lineId, 'self');
        }

        async function saveManagerLine(lineId) {
            await saveLine(lineId, 'manager');
        }

        async function saveLine(lineId, mode) {
            const row = document.querySelector(`[data-line-id="${lineId}"]`);
            setLineStatus(row, 'Saving...', 'amber');
            const selfScore = numberOrNull(row.querySelector('.selfScore').value);
            const managerScore = numberOrNull(row.querySelector('.managerScore').value);
            const selfRubricBandId = numberOrNull(row.querySelector('.selfRubricBand')?.value);
            const managerRubricBandId = numberOrNull(row.querySelector('.managerRubricBand')?.value);
            if (!validScore(selfScore) || !validScore(managerScore)) {
                showAlert('Scores must be between 0 and 100.', false);
                setLineStatus(row, 'Error', 'red');
                return;
            }
            const evidenceUrl = row.querySelector('.evidenceUrl').value;
            if (evidenceUrl && !validUrl(evidenceUrl)) {
                showAlert('Evidence must be a valid http or https URL.', false);
                setLineStatus(row, 'Invalid evidence', 'red');
                return;
            }
            const payload = {
                selfScore: selfRubricBandId ? null : selfScore,
                selfRubricBandId,
                selfComment: row.querySelector('.selfComment').value,
                managerScore: managerRubricBandId ? null : managerScore,
                managerRubricBandId,
                managerComment: row.querySelector('.managerComment').value,
                evidenceUrl
            };
            const response = await fetch(`/api/appraisals/lines/${lineId}/${mode}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            if (!response.ok) {
                showAlert(await response.text(), false);
                setLineStatus(row, 'Error', 'red');
                return;
            }
            await refreshWeightedScore();
            setLineStatus(row, 'Saved', 'green');
            updateProgress();
            showAlert('Line saved.', true);
        }

        async function refreshWeightedScore() {
            const response = await fetch(`/api/appraisals/${appraisalId}/lines/weighted-score`);
            if (response.ok) {
                document.getElementById('weightedScore').textContent = await response.text();
            }
        }

        function showAlert(message, success) {
            const alert = document.getElementById('scorecardAlert');
            alert.textContent = message;
            alert.className = success
                ? 'rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800'
                : 'rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800';
        }

        function numberOrNull(value) {
            const n = Number(value);
            return Number.isFinite(n) && value !== '' ? n : null;
        }

        function validScore(value) {
            return value === null || (value >= 0 && value <= 100);
        }

        function validUrl(value) {
            try {
                const url = new URL(value);
                return url.protocol === 'http:' || url.protocol === 'https:';
            } catch (e) {
                return false;
            }
        }

        function setLineStatus(row, text, color) {
            const status = row.querySelector('.lineSaveStatus');
            if (!status) return;
            const classes = {
                green: 'bg-emerald-100 text-emerald-700',
                amber: 'bg-amber-100 text-amber-700',
                red: 'bg-red-100 text-red-700'
            };
            status.className = `lineSaveStatus rounded-full px-2 py-1 text-xs font-bold ${classes[color] || 'bg-gray-100 text-gray-600'}`;
            status.textContent = text;
        }

        function applyRubricSelection(select) {
            const row = select.closest('tr');
            const option = select.selectedOptions[0];
            const isSelf = select.classList.contains('selfRubricBand');
            const scoreInput = row.querySelector(isSelf ? '.selfScore' : '.managerScore');
            const help = select.parentElement.querySelector('.rubricHelp');
            if (option && option.value) {
                scoreInput.value = option.dataset.score || '';
                scoreInput.disabled = true;
                if (help) help.textContent = `${option.dataset.range || ''} ${option.dataset.description || ''}`.trim();
            } else {
                scoreInput.disabled = scoreInput.hasAttribute('data-role-disabled');
                if (help) help.textContent = '';
            }
            updateProgress();
        }

        function updateProgress() {
            const rows = Array.from(document.querySelectorAll('[data-line-id]'));
            if (!rows.length) return;
            const completed = rows.filter(row => {
                const selfDone = numberOrNull(row.querySelector('.selfScore')?.value) !== null || numberOrNull(row.querySelector('.selfRubricBand')?.value) !== null;
                const managerDone = numberOrNull(row.querySelector('.managerScore')?.value) !== null || numberOrNull(row.querySelector('.managerRubricBand')?.value) !== null;
                return selfDone && managerDone;
            }).length;
            const pct = Math.round((completed * 100) / rows.length);
            document.getElementById('lineProgressText').textContent = `${pct}% complete (${completed}/${rows.length})`;
            document.getElementById('lineProgressBar').style.width = `${pct}%`;
        }

        document.querySelectorAll('.selfScore[disabled], .managerScore[disabled]').forEach(input => input.setAttribute('data-role-disabled', 'true'));
        document.querySelectorAll('.selfRubricBand, .managerRubricBand').forEach(select => {
            select.addEventListener('change', () => applyRubricSelection(select));
            applyRubricSelection(select);
        });
        document.querySelectorAll('.selfScore, .managerScore').forEach(input => input.addEventListener('input', updateProgress));
        updateProgress();
