const kpiDefinitions = window.kpiScorecardData?.kpiDefinitions || [];
        const frequencies = ['DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'BI_ANNUALLY', 'ANNUALLY', 'AS_REQUIRED', 'FOR_EVERY_RECRUITMENT'];
        let designerRows = [];
        let rowCounter = 0;
        let rubricBands = [];
        let rubricBandCounter = 0;
        let currentImportPreview = null;

        function switchDesignerTab(tab) {
            document.querySelectorAll('.designer-panel').forEach(panel => panel.classList.add('hidden'));
            document.getElementById(`${tab}Panel`).classList.remove('hidden');
            document.querySelectorAll('.designer-tab').forEach(button => button.classList.remove('active'));
            const activeButton = Array.from(document.querySelectorAll('.designer-tab'))
                .find(button => button.getAttribute('onclick') && button.getAttribute('onclick').includes(`'${tab}'`));
            if (activeButton) activeButton.classList.add('active');
        }

        function addDesignerRow(role) {
            const keyPrefix = role.toLowerCase();
            designerRows.push({
                clientKey: `${keyPrefix}_${++rowCounter}`,
                role,
                kpiId: '',
                parentClientKey: '',
                weight: role === 'PERSPECTIVE' ? '' : '0.00',
                frequency: '',
                mandatory: true
            });
            renderDesigner();
        }

        function removeDesignerRow(clientKey) {
            designerRows = designerRows.filter(row => row.clientKey !== clientKey && row.parentClientKey !== clientKey);
            renderDesigner();
        }

        function moveDesignerRow(clientKey, direction) {
            const index = designerRows.findIndex(row => row.clientKey === clientKey);
            const target = index + direction;
            if (index < 0 || target < 0 || target >= designerRows.length) return;
            const [row] = designerRows.splice(index, 1);
            designerRows.splice(target, 0, row);
            renderDesigner();
        }

        function updateDesignerRow(clientKey, field, value) {
            const row = designerRows.find(item => item.clientKey === clientKey);
            if (!row) return;
            row[field] = value;
            renderDesigner();
        }

        function parentOptions(row) {
            const allowed = designerRows.filter(candidate => {
                if (candidate.clientKey === row.clientKey) return false;
                if (row.role === 'OBJECTIVE') return candidate.role === 'PERSPECTIVE';
                if (row.role === 'INDICATOR') return candidate.role === 'OBJECTIVE' || candidate.role === 'PERSPECTIVE';
                return false;
            });
            return `<option value="">${row.role === 'PERSPECTIVE' ? 'Root' : 'Select parent'}</option>` +
                allowed.map(candidate => `<option value="${candidate.clientKey}" ${row.parentClientKey === candidate.clientKey ? 'selected' : ''}>${escapeHtml(rowLabel(candidate))}</option>`).join('');
        }

        function kpiOptions(selectedId) {
            return '<option value="">Select KPI</option>' + kpiDefinitions.map(kpi =>
                `<option value="${kpi.id}" ${String(selectedId) === String(kpi.id) ? 'selected' : ''}>${escapeHtml(kpi.code)} - ${escapeHtml(kpi.name)}</option>`
            ).join('');
        }

        function renderDesigner() {
            const tbody = document.getElementById('designerRows');
            tbody.innerHTML = designerRows.map(row => `
                <tr>
                    <td class="px-3 py-2"><div class="flex items-center gap-2">${roleBadge(row.role)} ${validationBadge(row)}</div></td>
                    <td class="px-3 py-2">
                        <select class="w-full rounded-lg border-gray-300 text-sm" onchange="updateDesignerRow('${row.clientKey}', 'kpiId', this.value)">
                            ${kpiOptions(row.kpiId)}
                        </select>
                    </td>
                    <td class="px-3 py-2">
                        <select class="w-full rounded-lg border-gray-300 text-sm" ${row.role === 'PERSPECTIVE' ? 'disabled' : ''} onchange="updateDesignerRow('${row.clientKey}', 'parentClientKey', this.value)">
                            ${parentOptions(row)}
                        </select>
                    </td>
                    <td class="px-3 py-2">
                        <input type="number" step="0.01" min="0.01" max="1" value="${row.weight}" class="w-full rounded-lg border-gray-300 text-sm" onchange="updateDesignerRow('${row.clientKey}', 'weight', this.value)" oninput="updateDesignerRow('${row.clientKey}', 'weight', this.value)">
                    </td>
                    <td class="px-3 py-2">
                        <select class="w-full rounded-lg border-gray-300 text-sm" onchange="updateDesignerRow('${row.clientKey}', 'frequency', this.value)">
                            <option value="">None</option>
                            ${frequencies.map(freq => `<option value="${freq}" ${row.frequency === freq ? 'selected' : ''}>${freq.replaceAll('_', ' ')}</option>`).join('')}
                        </select>
                    </td>
                    <td class="px-3 py-2 text-center">
                        <input type="checkbox" ${row.mandatory ? 'checked' : ''} onchange="updateDesignerRow('${row.clientKey}', 'mandatory', this.checked)">
                    </td>
                    <td class="px-3 py-2">
                        <div class="flex gap-1">
                            <button type="button" class="icon-btn bg-gray-50 text-gray-700 hover:bg-gray-100" onclick="moveDesignerRow('${row.clientKey}', -1)" title="Move up">↑</button>
                            <button type="button" class="icon-btn bg-gray-50 text-gray-700 hover:bg-gray-100" onclick="moveDesignerRow('${row.clientKey}', 1)" title="Move down">↓</button>
                            <button type="button" class="icon-btn bg-red-50 text-red-700 hover:bg-red-100" onclick="removeDesignerRow('${row.clientKey}')" title="Remove">x</button>
                        </div>
                    </td>
                </tr>
            `).join('');

            renderDesignerSummary();
            renderDesignerTree();
            renderDesignerValidation();
        }

        function renderDesignerSummary() {
            const total = designerRows
                .filter(row => !row.parentClientKey)
                .reduce((sum, row) => sum + numeric(row.weight), 0);
            document.getElementById('designerEffectiveWeight').textContent = total.toFixed(2);
            document.getElementById('designerRowCount').textContent = designerRows.length;
            const status = document.getElementById('designerWeightStatus');
            const valid = Math.abs(total - 1) <= 0.001;
            status.textContent = valid ? 'Balanced' : 'Must equal 1.00';
            status.className = valid ? 'text-sm font-bold text-emerald-600' : 'text-sm font-bold text-red-600';
        }

        function renderDesignerTree() {
            const root = document.getElementById('designerTree');
            if (designerRows.length === 0) {
                root.textContent = 'Add rows to preview the scorecard tree.';
                return;
            }
            const children = {};
            designerRows.forEach(row => {
                const parent = row.parentClientKey || '__root';
                children[parent] = children[parent] || [];
                children[parent].push(row);
            });
            root.innerHTML = renderTreeChildren(children, '__root', 0);
        }

        function renderTreeChildren(children, parentKey, depth) {
            return (children[parentKey] || []).map(row => `
                <div class="${depth > 0 ? 'tree-line ml-4 pl-4' : ''} py-1" style="margin-left:${depth * 10}px">
                    <div class="flex items-center justify-between gap-3">
                        <div>${roleBadge(row.role)} <span class="font-medium">${escapeHtml(rowLabel(row))}</span></div>
                        <span class="text-xs font-bold text-gray-500">${numeric(row.weight).toFixed(2)} / children ${childRollup(children, row.clientKey).toFixed(2)}</span>
                    </div>
                    ${renderTreeChildren(children, row.clientKey, depth + 1)}
                </div>
            `).join('');
        }

        function renderDesignerValidation() {
            const errors = validateDesigner();
            const list = document.getElementById('designerValidation');
            if (errors.length === 0) {
                list.innerHTML = '<li class="text-emerald-700">Template is ready to save.</li>';
            } else {
                list.innerHTML = errors.map(error => `<li class="text-red-700">${escapeHtml(error)}</li>`).join('');
            }
        }

        function validateDesigner() {
            const errors = [];
            if (!document.getElementById('templateName').value.trim()) errors.push('Template name is required.');
            if (designerRows.length === 0) errors.push('At least one scorecard row is required.');
            const selected = new Set();
            designerRows.forEach(row => {
                if (!row.kpiId) errors.push(`${row.clientKey}: KPI definition is required.`);
                if (row.kpiId && selected.has(String(row.kpiId))) errors.push(`${rowLabel(row)} is selected more than once.`);
                if (row.kpiId) selected.add(String(row.kpiId));
                if (row.role !== 'PERSPECTIVE' && !row.parentClientKey) errors.push(`${rowLabel(row)} requires a parent.`);
                if (numeric(row.weight) <= 0) errors.push(`${rowLabel(row)} requires a positive weight.`);
            });
            const roots = designerRows.filter(row => !row.parentClientKey).reduce((sum, row) => sum + numeric(row.weight), 0);
            if (Math.abs(roots - 1) > 0.001) errors.push(`Root perspective weights must total 1.00. Current total is ${roots.toFixed(2)}.`);
            designerRows.forEach(parent => {
                const children = designerRows.filter(row => row.parentClientKey === parent.clientKey);
                if (children.length > 0) {
                    const childTotal = children.reduce((sum, row) => sum + numeric(row.weight), 0);
                    if (Math.abs(childTotal - numeric(parent.weight)) > 0.001) {
                        errors.push(`Children under ${rowLabel(parent)} must total ${numeric(parent.weight).toFixed(2)}. Current total is ${childTotal.toFixed(2)}.`);
                    }
                }
            });
            return errors;
        }

        function resetDesigner() {
            document.getElementById('templateId').value = '';
            document.getElementById('templateName').value = '';
            document.getElementById('templateRole').value = '';
            document.getElementById('templateRubric').value = '';
            designerRows = [];
            rowCounter = 0;
            renderDesigner();
            switchDesignerTab('designer');
        }

        async function loadTemplate(templateId) {
            const response = await fetch(`/api/kpi/scorecards/templates/${templateId}`);
            if (!response.ok) {
                showDesignerAlert('Failed to load template.', false);
                return;
            }
            const template = await response.json();
            document.getElementById('templateId').value = template.id || '';
            document.getElementById('templateName').value = template.name || '';
            document.getElementById('templateRole').value = template.roleName || '';
            document.getElementById('templateRubric').value = template.defaultRubricId || '';
            designerRows = (template.items || []).map(item => {
                rowCounter++;
                return {
                    clientKey: item.clientKey || `item_${rowCounter}`,
                    role: inferRole(item.kpiId, item.parentClientKey),
                    kpiId: item.kpiId || '',
                    parentClientKey: item.parentClientKey || '',
                    weight: item.weight || '0.00',
                    frequency: item.frequency || '',
                    mandatory: !!item.mandatory
                };
            });
            renderDesigner();
            switchDesignerTab('designer');
            loadTemplateVersions(template.id);
            showDesignerAlert(`Loaded ${template.name}.`, true);
        }

        async function loadTemplateVersions(templateId) {
            const panel = document.getElementById('templateVersionHistory');
            if (!panel) return;
            const response = await fetch(`/api/kpi/scorecards/templates/${templateId}/versions`);
            if (!response.ok) {
                panel.textContent = 'Unable to load version history.';
                return;
            }
            const versions = await response.json();
            if (!versions.length) {
                panel.textContent = 'No published versions yet.';
                return;
            }
            panel.innerHTML = `<div class="space-y-2">${versions.map(version => `
                <div class="rounded-lg border border-gray-200 p-3">
                    <div class="flex items-center justify-between"><span class="font-bold">v${version.versionNumber}</span><span class="text-xs text-gray-500">${escapeHtml(version.createdAt || '')}</span></div>
                    <div class="text-xs text-gray-500">Published by ${escapeHtml(version.publishedBy || 'system')}</div>
                </div>
            `).join('')}</div>`;
        }

        async function saveDesignedTemplate(publish) {
            const errors = validateDesigner();
            const blockingErrors = publish ? errors : errors.filter(error => error === 'Template name is required.');
            if (blockingErrors.length > 0) {
                showDesignerAlert(blockingErrors[0], false);
                return;
            }
            if (publish && !await confirmDesignerAction('Publish this template? Published templates become immutable and create a version snapshot.', 'Publish')) {
                return;
            }
            const payload = {
                name: document.getElementById('templateName').value.trim(),
                roleName: document.getElementById('templateRole').value.trim(),
                defaultRubricId: document.getElementById('templateRubric').value || null,
                active: !!publish,
                items: designerRows.map((row, index) => ({
                    clientKey: row.clientKey,
                    parentClientKey: row.parentClientKey || null,
                    kpiId: Number(row.kpiId),
                    weight: Number(row.weight),
                    mandatory: !!row.mandatory,
                    sortOrder: index,
                    frequency: row.frequency || null,
                    rubricId: null
                }))
            };
            const templateId = document.getElementById('templateId').value;
            const response = await fetch(templateId ? `/api/kpi/scorecards/templates/${templateId}` : '/api/kpi/scorecards/templates', {
                method: templateId ? 'PUT' : 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            if (!response.ok) {
                const text = await response.text();
                showDesignerAlert(text || 'Failed to save template.', false);
                return;
            }
            showDesignerAlert(publish ? 'Template published successfully. Reloading library...' : 'Draft saved successfully. Reloading library...', true);
            setTimeout(() => window.location.reload(), 900);
        }

        async function publishTemplate(templateId) {
            if (!await confirmDesignerAction('Publish this draft template and create a new version snapshot?', 'Publish')) return;
            await postTemplateAction(templateId, 'publish', 'Template published successfully.');
        }

        async function duplicateTemplate(templateId) {
            await postTemplateAction(templateId, 'duplicate', 'Template duplicated successfully.');
        }

        async function archiveTemplate(templateId) {
            if (!await confirmDesignerAction('Archive this scorecard template?', 'Archive')) return;
            const response = await fetch(`/api/kpi/scorecards/templates/${templateId}/archive`, { method: 'POST' });
            if (response.ok) {
                showDesignerAlert('Template archived successfully. Reloading library...', true);
                setTimeout(() => window.location.reload(), 900);
                return;
            }
            const text = await response.text();
            if (await confirmDesignerAction(`${text || 'Template has active assignments.'} Force archive and deactivate active assignments?`, 'Force Archive')) {
                const forced = await fetch(`/api/kpi/scorecards/templates/${templateId}/archive?force=true`, { method: 'POST' });
                if (!forced.ok) {
                    showDesignerAlert(await forced.text(), false);
                    return;
                }
                showDesignerAlert('Template force archived successfully. Reloading library...', true);
                setTimeout(() => window.location.reload(), 900);
            }
        }

        function confirmDesignerAction(message, actionLabel) {
            return new Promise(resolve => {
                const overlay = document.createElement('div');
                overlay.className = 'fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4';
                overlay.innerHTML = `
                    <div class="w-full max-w-md rounded-lg bg-white p-5 shadow-xl dark:bg-gray-800">
                        <h3 class="text-lg font-bold text-gray-900 dark:text-white">Confirm Action</h3>
                        <p class="mt-2 text-sm text-gray-600 dark:text-gray-300">${escapeHtml(message)}</p>
                        <div class="mt-5 flex justify-end gap-2">
                            <button type="button" class="cancel rounded-lg border border-gray-300 px-4 py-2 text-sm font-bold hover:bg-gray-100 dark:border-gray-700 dark:hover:bg-gray-900">Cancel</button>
                            <button type="button" class="confirm rounded-lg bg-red-600 px-4 py-2 text-sm font-bold text-white hover:bg-red-700">${escapeHtml(actionLabel || 'Confirm')}</button>
                        </div>
                    </div>
                `;
                document.body.appendChild(overlay);
                overlay.querySelector('.cancel').addEventListener('click', () => {
                    overlay.remove();
                    resolve(false);
                });
                overlay.querySelector('.confirm').addEventListener('click', () => {
                    overlay.remove();
                    resolve(true);
                });
            });
        }

        async function postTemplateAction(templateId, action, successMessage) {
            const response = await fetch(`/api/kpi/scorecards/templates/${templateId}/${action}`, { method: 'POST' });
            if (!response.ok) {
                const text = await response.text();
                showDesignerAlert(text || `Failed to ${action} template.`, false);
                return;
            }
            showDesignerAlert(`${successMessage} Reloading library...`, true);
            setTimeout(() => window.location.reload(), 900);
        }

        function toggleScorecardScope() {
            const value = document.getElementById('scopeType').value;
            const scopes = {
                employee: document.getElementById('employeeScope'),
                jobStep: document.getElementById('jobStepScope'),
                department: document.getElementById('departmentScope')
            };
            Object.entries(scopes).forEach(([key, element]) => {
                const active = key === value;
                element.classList.toggle('hidden', !active);
                const select = element.querySelector('select');
                select.disabled = !active;
                if (!active) select.value = '';
            });
        }

        async function previewTemplateApply() {
            const payload = applyPayload();
            if (!payload.templateId) {
                showDesignerAlert('Select a template before previewing apply.', false);
                return;
            }
            const response = await fetch('/api/kpi/scorecards/templates/apply-preview', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const panel = document.getElementById('applyPreview');
            panel.classList.remove('hidden');
            if (!response.ok) {
                panel.innerHTML = `<p class="text-red-700">${escapeHtml(await response.text())}</p>`;
                return;
            }
            const preview = await response.json();
            panel.innerHTML = `
                <div class="grid grid-cols-1 md:grid-cols-4 gap-3 mb-4">
                    <div><p class="text-xs font-bold uppercase text-gray-500">Scope</p><p class="font-bold">${escapeHtml(preview.scope)} #${preview.ownerId}</p></div>
                    <div><p class="text-xs font-bold uppercase text-gray-500">Template Rows</p><p class="font-bold">${preview.templateAssignments.length}</p></div>
                    <div><p class="text-xs font-bold uppercase text-gray-500">Add</p><p class="font-bold text-emerald-700">${preview.assignmentsToAdd.length}</p></div>
                    <div><p class="text-xs font-bold uppercase text-gray-500">Remove</p><p class="font-bold text-red-700">${preview.assignmentsToRemove.length}</p></div>
                </div>
                ${preview.warnings && preview.warnings.length ? `<div class="mb-3 text-amber-700">${preview.warnings.map(escapeHtml).join('<br>')}</div>` : ''}
                <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div><p class="font-bold mb-2">To Add</p>${assignmentList(preview.assignmentsToAdd)}</div>
                    <div><p class="font-bold mb-2">To Remove</p>${assignmentList(preview.assignmentsToRemove)}</div>
                </div>
            `;
        }

        async function previewImport() {
            const form = document.getElementById('importPreviewForm');
            const data = new FormData(form);
            const panel = document.getElementById('importPreview');
            panel.classList.remove('hidden');
            panel.innerHTML = '<p class="text-gray-500">Parsing workbook...</p>';
            const response = await fetch('/api/kpi/scorecards/templates/import/preview', {
                method: 'POST',
                body: data
            });
            if (!response.ok) {
                panel.innerHTML = `<p class="text-red-700">${escapeHtml(await response.text())}</p>`;
                return;
            }
            currentImportPreview = await response.json();
            renderImportPreview();
        }

        function renderImportPreview() {
            const panel = document.getElementById('importPreview');
            if (!currentImportPreview) return;
            currentImportPreview.totalWeight = (currentImportPreview.rows || [])
                .reduce((sum, row) => sum + numeric(row.weight), 0);
            currentImportPreview.errors = validateImportPreviewClient();
            panel.innerHTML = `
                <div class="grid grid-cols-1 md:grid-cols-3 gap-3 mb-4">
                    <div><p class="text-xs font-bold uppercase text-gray-500">Template</p><p class="font-bold">${escapeHtml(currentImportPreview.templateName)}</p></div>
                    <div><p class="text-xs font-bold uppercase text-gray-500">Rows</p><p class="font-bold">${currentImportPreview.rows.length}</p></div>
                    <div><p class="text-xs font-bold uppercase text-gray-500">Weight Points</p><p class="font-bold">${Number(currentImportPreview.totalWeight).toFixed(2)}</p></div>
                </div>
                <div class="mb-3 flex flex-wrap gap-2">
                    <button type="button" onclick="addImportPreviewRow()" class="rounded border border-blue-300 px-3 py-1.5 text-xs font-bold text-blue-700">Add Row</button>
                    <button type="button" onclick="downloadImportErrorReport()" class="rounded border border-red-300 px-3 py-1.5 text-xs font-bold text-red-700">Download Error Report</button>
                </div>
                ${currentImportPreview.errors && currentImportPreview.errors.length ? `<div class="mb-3 rounded border border-red-200 bg-red-50 p-3 text-red-700"><p class="font-bold">Blocking errors</p>${currentImportPreview.errors.map(escapeHtml).join('<br>')}</div>` : ''}
                ${currentImportPreview.warnings && currentImportPreview.warnings.length ? `<div class="mb-3 rounded border border-amber-200 bg-amber-50 p-3 text-amber-700"><p class="font-bold">Warnings</p>${currentImportPreview.warnings.map(escapeHtml).join('<br>')}</div>` : ''}
                ${duplicatePanel()}
                <div class="max-h-72 overflow-auto rounded-lg border border-gray-200 bg-white">
                    <table class="w-full min-w-[780px] text-xs">
                        <thead class="bg-gray-50 text-left uppercase text-gray-500"><tr><th class="px-3 py-2">Row</th><th class="px-3 py-2">Perspective</th><th class="px-3 py-2">Objective</th><th class="px-3 py-2">Indicator</th><th class="px-3 py-2">Timeline</th><th class="px-3 py-2">Measure</th><th class="px-3 py-2">Weight</th><th class="px-3 py-2">Action</th></tr></thead>
                        <tbody>${currentImportPreview.rows.map((row, index) => `<tr class="border-t border-gray-100">
                            <td class="px-3 py-2">${row.rowNumber || ''}</td>
                            <td class="px-3 py-2"><input class="w-40 rounded border-gray-300 text-xs" value="${escapeHtml(row.perspective)}" oninput="updateImportPreviewRow(${index}, 'perspective', this.value)"></td>
                            <td class="px-3 py-2"><input class="w-40 rounded border-gray-300 text-xs" value="${escapeHtml(row.objective || '')}" oninput="updateImportPreviewRow(${index}, 'objective', this.value)"></td>
                            <td class="px-3 py-2"><input class="w-48 rounded border-gray-300 text-xs" value="${escapeHtml(row.indicator)}" oninput="updateImportPreviewRow(${index}, 'indicator', this.value)"></td>
                            <td class="px-3 py-2"><input class="w-32 rounded border-gray-300 text-xs" value="${escapeHtml(row.timeline || '')}" oninput="updateImportPreviewRow(${index}, 'timeline', this.value)"></td>
                            <td class="px-3 py-2"><input class="w-40 rounded border-gray-300 text-xs" value="${escapeHtml(row.measure || '')}" oninput="updateImportPreviewRow(${index}, 'measure', this.value)"></td>
                            <td class="px-3 py-2"><input type="number" step="0.01" class="w-24 rounded border-gray-300 text-xs" value="${row.weight ?? ''}" oninput="updateImportPreviewRow(${index}, 'weight', this.value)"></td>
                            <td class="px-3 py-2"><button type="button" onclick="removeImportPreviewRow(${index})" class="rounded border border-red-200 px-2 py-1 text-red-700">Remove</button></td>
                        </tr>`).join('')}</tbody>
                    </table>
                </div>
            `;
        }

        function updateImportPreviewRow(index, field, value) {
            if (!currentImportPreview || !currentImportPreview.rows[index]) return;
            currentImportPreview.rows[index][field] = field === 'weight' ? numberOrNull(value) : value;
            currentImportPreview.totalWeight = currentImportPreview.rows.reduce((sum, row) => sum + numeric(row.weight), 0);
            currentImportPreview.errors = validateImportPreviewClient();
            const total = document.querySelector('#importPreview .font-bold:last-child');
            if (total) total.textContent = Number(currentImportPreview.totalWeight).toFixed(2);
        }

        function addImportPreviewRow() {
            if (!currentImportPreview) return;
            currentImportPreview.rows.push({ rowNumber: currentImportPreview.rows.length + 1, perspective: '', objective: '', indicator: '', timeline: '', measure: '', weight: null });
            renderImportPreview();
        }

        function removeImportPreviewRow(index) {
            if (!currentImportPreview) return;
            currentImportPreview.rows.splice(index, 1);
            renderImportPreview();
        }

        function duplicatePanel() {
            const duplicates = duplicateImportRows();
            if (!duplicates.length) return '';
            return `<div class="mb-3 rounded border border-amber-200 bg-amber-50 p-3 text-amber-800"><p class="font-bold">Duplicate detection</p>${duplicates.map(escapeHtml).join('<br>')}</div>`;
        }

        function duplicateImportRows() {
            if (!currentImportPreview) return [];
            const seen = new Map();
            const duplicates = [];
            (currentImportPreview.rows || []).forEach((row, index) => {
                const key = `${String(row.perspective || '').trim().toLowerCase()}|${String(row.objective || '').trim().toLowerCase()}|${String(row.indicator || '').trim().toLowerCase()}`;
                if (seen.has(key)) duplicates.push(`Row ${row.rowNumber || index + 1} duplicates row ${seen.get(key)}.`);
                else seen.set(key, row.rowNumber || index + 1);
            });
            return duplicates;
        }

        function downloadImportErrorReport() {
            if (!currentImportPreview) return;
            const lines = ['severity,message'];
            (currentImportPreview.errors || []).forEach(error => lines.push(`ERROR,"${String(error).replaceAll('"', '""')}"`));
            (currentImportPreview.warnings || []).forEach(warning => lines.push(`WARNING,"${String(warning).replaceAll('"', '""')}"`));
            duplicateImportRows().forEach(warning => lines.push(`WARNING,"${warning.replaceAll('"', '""')}"`));
            const blob = new Blob([lines.join('\n')], { type: 'text/csv' });
            const link = document.createElement('a');
            link.href = URL.createObjectURL(blob);
            link.download = 'kpi-import-error-report.csv';
            link.click();
            URL.revokeObjectURL(link.href);
        }

        function validateImportPreviewClient() {
            if (!currentImportPreview) return [];
            const errors = [];
            const rows = currentImportPreview.rows || [];
            const total = rows.reduce((sum, row) => sum + numeric(row.weight), 0);
            if (rows.length === 0) errors.push('No importable scorecard rows were detected.');
            if (Math.abs(total - 100) > 0.001) errors.push(`Imported row weights total ${total.toFixed(2)} points; published templates require exactly 100 points.`);
            const seen = new Set();
            rows.forEach((row, index) => {
                if (!String(row.perspective || '').trim()) errors.push(`Row ${row.rowNumber || index + 1}: perspective is required.`);
                if (!String(row.indicator || '').trim()) errors.push(`Row ${row.rowNumber || index + 1}: indicator is required.`);
                if (numeric(row.weight) <= 0) errors.push(`Row ${row.rowNumber || index + 1}: weight must be greater than zero.`);
                const key = `${String(row.perspective || '').trim().toLowerCase()}|${String(row.objective || '').trim().toLowerCase()}|${String(row.indicator || '').trim().toLowerCase()}`;
                if (seen.has(key)) errors.push(`Row ${row.rowNumber || index + 1}: duplicate indicator under the same perspective/objective.`);
                seen.add(key);
            });
            return errors;
        }

        async function confirmImport() {
            if (!currentImportPreview) {
                showDesignerAlert('Preview an import before confirming.', false);
                return;
            }
            if (currentImportPreview.errors && currentImportPreview.errors.length) {
                showDesignerAlert('Resolve import preview errors before confirming.', false);
                return;
            }
            const response = await fetch('/api/kpi/scorecards/templates/import/confirm', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(currentImportPreview)
            });
            if (!response.ok) {
                showDesignerAlert(await response.text(), false);
                return;
            }
            const result = await response.json();
            showDesignerAlert(`Imported ${result.templateName} with ${result.templateItemsCreated} template items. Reloading...`, true);
            setTimeout(() => window.location.reload(), 1000);
        }

        function applyPayload() {
            const form = document.getElementById('applyTemplateForm');
            const data = new FormData(form);
            return {
                templateId: numberOrNull(data.get('templateId')),
                employeeId: numberOrNull(data.get('employeeId')),
                jobStepId: numberOrNull(data.get('jobStepId')),
                departmentId: numberOrNull(data.get('departmentId')),
                replaceExisting: data.get('replaceExisting') === 'true'
            };
        }

        function assignmentList(items) {
            if (!items || items.length === 0) return '<p class="text-gray-500">None</p>';
            return `<ul class="space-y-1">${items.map(item => `<li>${escapeHtml(item.kpiCode || '')} - ${escapeHtml(item.name || '')} <span class="font-bold">${item.weight}</span></li>`).join('')}</ul>`;
        }

        async function loadAssignmentHistory() {
            const response = await fetch('/api/kpi/scorecards/templates/assignments');
            const container = document.getElementById('assignmentHistory');
            if (!response.ok) {
                container.innerHTML = '<p class="text-red-700">Failed to load assignment history.</p>';
                return;
            }
            const rows = await response.json();
            if (!rows.length) {
                container.textContent = 'No assignment history yet.';
                return;
            }
            container.innerHTML = `
                <div class="overflow-x-auto">
                    <table class="w-full min-w-[820px] text-sm">
                        <thead class="bg-gray-50 text-left text-xs uppercase text-gray-500"><tr><th class="px-3 py-2">Template</th><th class="px-3 py-2">Version</th><th class="px-3 py-2">Scope</th><th class="px-3 py-2">Owner</th><th class="px-3 py-2">Valid</th><th class="px-3 py-2">Applied By</th><th class="px-3 py-2">Status</th></tr></thead>
                        <tbody>${rows.map(row => `<tr class="border-t border-gray-100"><td class="px-3 py-2">${escapeHtml(row.templateName)}</td><td class="px-3 py-2">${row.templateVersionNumber ? `v${row.templateVersionNumber}` : '-'}</td><td class="px-3 py-2">${escapeHtml(row.scope)}</td><td class="px-3 py-2">${escapeHtml(row.ownerName)}</td><td class="px-3 py-2">${escapeHtml(row.validFrom || '-')} to ${escapeHtml(row.validTo || 'open')}</td><td class="px-3 py-2">${escapeHtml(row.appliedBy || 'system')}</td><td class="px-3 py-2">${row.active ? 'Active' : 'Inactive'}</td></tr>`).join('')}</tbody>
                    </table>
                </div>`;
        }

        function resetRubricDesigner() {
            document.getElementById('rubricId').value = '';
            document.getElementById('rubricName').value = '';
            document.getElementById('rubricDescription').value = '';
            rubricBands = [];
            rubricBandCounter = 0;
            renderRubricBands();
        }

        async function loadRubric(id) {
            const response = await fetch(`/api/kpi/scorecards/rubrics/${id}`);
            if (!response.ok) {
                showDesignerAlert('Failed to load rubric.', false);
                return;
            }
            const rubric = await response.json();
            document.getElementById('rubricId').value = rubric.id || '';
            document.getElementById('rubricName').value = rubric.name || '';
            document.getElementById('rubricDescription').value = rubric.description || '';
            rubricBands = (rubric.bands || []).map(band => ({...band, clientKey: `band_${++rubricBandCounter}`}));
            renderRubricBands();
            switchDesignerTab('rubrics');
        }

        function addRubricBand() {
            rubricBands.push({ clientKey: `band_${++rubricBandCounter}`, label: '', minScore: '', maxScore: '', numericScore: '', description: '' });
            renderRubricBands();
        }

        function updateRubricBand(clientKey, field, value) {
            const band = rubricBands.find(item => item.clientKey === clientKey);
            if (!band) return;
            band[field] = value;
        }

        function removeRubricBand(clientKey) {
            rubricBands = rubricBands.filter(item => item.clientKey !== clientKey);
            renderRubricBands();
        }

        function renderRubricBands() {
            const tbody = document.getElementById('rubricBands');
            tbody.innerHTML = rubricBands.map(band => `
                <tr>
                    <td class="px-3 py-2"><input value="${escapeHtml(band.label)}" oninput="updateRubricBand('${band.clientKey}', 'label', this.value)" class="w-full rounded-lg border-gray-300 text-sm"></td>
                    <td class="px-3 py-2"><input type="number" step="0.01" value="${band.minScore ?? ''}" oninput="updateRubricBand('${band.clientKey}', 'minScore', this.value)" class="w-full rounded-lg border-gray-300 text-sm"></td>
                    <td class="px-3 py-2"><input type="number" step="0.01" value="${band.maxScore ?? ''}" oninput="updateRubricBand('${band.clientKey}', 'maxScore', this.value)" class="w-full rounded-lg border-gray-300 text-sm"></td>
                    <td class="px-3 py-2"><input type="number" step="0.01" value="${band.numericScore ?? ''}" oninput="updateRubricBand('${band.clientKey}', 'numericScore', this.value)" class="w-full rounded-lg border-gray-300 text-sm"></td>
                    <td class="px-3 py-2"><input value="${escapeHtml(band.description)}" oninput="updateRubricBand('${band.clientKey}', 'description', this.value)" class="w-full rounded-lg border-gray-300 text-sm"></td>
                    <td class="px-3 py-2"><button type="button" onclick="removeRubricBand('${band.clientKey}')" class="icon-btn bg-red-50 text-red-700 hover:bg-red-100">x</button></td>
                </tr>
            `).join('');
        }

        async function saveRubric() {
            const payload = {
                name: document.getElementById('rubricName').value.trim(),
                description: document.getElementById('rubricDescription').value.trim(),
                active: true,
                bands: rubricBands.map((band, index) => ({
                    label: band.label,
                    minScore: numberOrNull(band.minScore),
                    maxScore: numberOrNull(band.maxScore),
                    numericScore: numberOrNull(band.numericScore),
                    description: band.description,
                    sortOrder: index
                }))
            };
            const id = document.getElementById('rubricId').value;
            const response = await fetch(id ? `/api/kpi/scorecards/rubrics/${id}` : '/api/kpi/scorecards/rubrics', {
                method: id ? 'PUT' : 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            if (!response.ok) {
                showDesignerAlert(await response.text(), false);
                return;
            }
            showDesignerAlert('Rubric saved successfully. Reloading...', true);
            setTimeout(() => window.location.reload(), 900);
        }

        function roleBadge(role) {
            const classes = {
                PERSPECTIVE: 'bg-blue-100 text-blue-800',
                OBJECTIVE: 'bg-slate-100 text-slate-800',
                INDICATOR: 'bg-emerald-100 text-emerald-800'
            };
            return `<span class="row-chip ${classes[role] || 'bg-gray-100 text-gray-800'}">${role}</span>`;
        }

        function validationBadge(row) {
            const ok = row.kpiId && numeric(row.weight) > 0 && (row.role === 'PERSPECTIVE' || row.parentClientKey);
            return `<span class="rounded-full px-2 py-0.5 text-[10px] font-bold ${ok ? 'bg-emerald-100 text-emerald-700' : 'bg-red-100 text-red-700'}">${ok ? 'OK' : 'Check'}</span>`;
        }

        function childRollup(children, clientKey) {
            return (children[clientKey] || []).reduce((sum, child) => sum + numeric(child.weight), 0);
        }

        function rowLabel(row) {
            const kpi = kpiDefinitions.find(item => String(item.id) === String(row.kpiId));
            return kpi ? kpi.name : row.clientKey;
        }

        function inferRole(kpiId, parentClientKey) {
            const kpi = kpiDefinitions.find(item => String(item.id) === String(kpiId));
            if (kpi && kpi.hierarchyRole) return kpi.hierarchyRole;
            if (!parentClientKey) return 'PERSPECTIVE';
            const parent = designerRows.find(row => row.clientKey === parentClientKey);
            return parent && parent.role === 'PERSPECTIVE' ? 'OBJECTIVE' : 'INDICATOR';
        }

        function showDesignerAlert(message, success) {
            const alert = document.getElementById('designerAlert');
            alert.textContent = message;
            alert.className = success
                ? 'rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800'
                : 'rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800';
        }

        function numeric(value) {
            const n = parseFloat(value);
            return Number.isFinite(n) ? n : 0;
        }

        function numberOrNull(value) {
            const n = Number(value);
            return Number.isFinite(n) && value !== '' && value !== null ? n : null;
        }

        function escapeHtml(value) {
            return String(value || '').replace(/[&<>"']/g, char => ({
                '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
            }[char]));
        }

        toggleScorecardScope();
        renderDesigner();
