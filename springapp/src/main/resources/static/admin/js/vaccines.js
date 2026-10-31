/**
 * Vaccines Domain & UI Controller
 */

// --- API Service Layer ---
const VaccinesApi = {
    getVaccines: () => ApiClient.get('/admin/vaccines'),
    createVaccine: (payload) => ApiClient.post('/admin/vaccines', payload),
    updateVaccine: (id, payload) => ApiClient.put(`/admin/vaccines/${id}`, payload),
    deleteVaccine: (id) => ApiClient.delete(`/admin/vaccines/${id}`),
    getSyncPreview: () => ApiClient.post('/admin/vaccines/sync/preview'),
    applySync: (payload) => ApiClient.post('/admin/vaccines/sync/apply', payload),
    triggerBackfill: () => ApiClient.post('/admin/vaccines/sync/backfill')
};

// --- UI / Render Layer ---
const VaccinesUI = {
    currentPreviewData: null,

    renderVaccines(vaccines) {
        const tbody = document.getElementById('vaccines-table-body');
        if (!tbody) return;
        tbody.innerHTML = '';

        vaccines.sort((a, b) => {
            const ageA = (a.scheduledAgeMonths || 0) * 30 + (a.scheduledAgeDays || 0);
            const ageB = (b.scheduledAgeMonths || 0) * 30 + (b.scheduledAgeDays || 0);
            return ageA - ageB;
        });

        vaccines.forEach(v => {
            const tr = document.createElement('tr');
            let ageStr = '';
            if (v.scheduledAgeMonths) ageStr += `${v.scheduledAgeMonths} міс. `;
            if (v.scheduledAgeDays) ageStr += `${v.scheduledAgeDays} дн.`;
            if (!ageStr) ageStr = '-';

            tr.innerHTML = `
                <td><strong>${AdminAuth.escapeHtml(v.name)}</strong></td>
                <td>${AdminAuth.escapeHtml(v.disease)}</td>
                <td>${v.doseNumber} з ${v.totalDoses}</td>
                <td><span class="badge">${v.type === 'VACCINATION' ? 'Вакцинація' : 'Ревакцинація'}</span></td>
                <td>${ageStr}</td>
                <td><small>${AdminAuth.escapeHtml(v.notes) || '-'}</small></td>
                <td>
                    <button class="btn btn-sm" onclick="editVaccine('${v.id}')">Редагувати</button>
                    <button class="btn btn-sm" style="background-color: var(--danger-color);" onclick="deleteVaccine('${v.id}')">Видалити</button>
                </td>
            `;
            tbody.appendChild(tr);
        });
    },

    renderSyncModal(data) {
        const newBody = document.getElementById('sync-new-body');
        const changedBody = document.getElementById('sync-changed-body');
        const missingBody = document.getElementById('sync-missing-body');

        if (!newBody || !changedBody || !missingBody) return;

        newBody.innerHTML = '';
        changedBody.innerHTML = '';
        missingBody.innerHTML = '';

        if (!data.newVaccines || data.newVaccines.length === 0) {
            newBody.innerHTML = '<tr><td colspan="6" style="text-align: center; color: #6c757d;">Немає нових вакцин</td></tr>';
        } else {
            data.newVaccines.forEach((item, idx) => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td><input type="checkbox" class="new-item-check" data-index="${idx}" checked></td>
                    <td><strong>${AdminAuth.escapeHtml(item.name)}</strong></td>
                    <td>${AdminAuth.escapeHtml(item.disease)}</td>
                    <td>${item.doseNumber} з ${item.totalDoses}</td>
                    <td>${item.type}</td>
                    <td>${this.formatAge(item.scheduledAgeMonths, item.scheduledAgeDays)}</td>
                `;
                newBody.appendChild(tr);
            });
        }

        if (!data.changedVaccines || data.changedVaccines.length === 0) {
            changedBody.innerHTML = '<tr><td colspan="6" style="text-align: center; color: #6c757d;">Немає змін</td></tr>';
        } else {
            data.changedVaccines.forEach((pair, idx) => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td><input type="checkbox" class="changed-item-check" data-index="${idx}" checked></td>
                    <td><strong>${AdminAuth.escapeHtml(pair.proposed.name)}</strong></td>
                    <td>${AdminAuth.escapeHtml(pair.proposed.disease)}</td>
                    <td>${pair.proposed.doseNumber} з ${pair.proposed.totalDoses}</td>
                    <td>${this.formatAge(pair.current.scheduledAgeMonths, pair.current.scheduledAgeDays)}</td>
                    <td style="color: #b06000;"><strong>${this.formatAge(pair.proposed.scheduledAgeMonths, pair.proposed.scheduledAgeDays)}</strong></td>
                `;
                changedBody.appendChild(tr);
            });
        }

        if (!data.missingVaccines || data.missingVaccines.length === 0) {
            missingBody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: #6c757d;">Усі вакцини знайдено на сайті</td></tr>';
        } else {
            data.missingVaccines.forEach(item => {
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td><strong>${AdminAuth.escapeHtml(item.name)}</strong></td>
                    <td>${AdminAuth.escapeHtml(item.disease)}</td>
                    <td>${item.doseNumber} з ${item.totalDoses}</td>
                    <td>${item.type}</td>
                    <td>${this.formatAge(item.scheduledAgeMonths, item.scheduledAgeDays)}</td>
                `;
                missingBody.appendChild(tr);
            });
        }
    },

    formatAge(months, days) {
        let res = '';
        if (months) res += `${months} міс. `;
        if (days) res += `${days} дн.`;
        return res.trim() || '0 дн.';
    },

    resetForm() {
        const form = document.getElementById('vaccine-form');
        if (form) form.reset();
        document.getElementById('vaccine-id').value = '';
        document.getElementById('form-title').textContent = 'Додати нове щеплення';
        document.getElementById('submit-btn').textContent = 'Зберегти щеплення';
        document.getElementById('cancel-btn').style.display = 'none';
    }
};

window.openSyncModal = async function() {
    const modal = document.getElementById('sync-modal');
    const loading = document.getElementById('sync-loading');
    const content = document.getElementById('sync-content');

    if (modal) modal.style.display = 'flex';
    if (loading) loading.style.display = 'block';
    if (content) content.style.display = 'none';

    try {
        VaccinesUI.currentPreviewData = await VaccinesApi.getSyncPreview();
        VaccinesUI.renderSyncModal(VaccinesUI.currentPreviewData);
        if (loading) loading.style.display = 'none';
        if (content) content.style.display = 'block';
    } catch (err) {
        if (loading) loading.innerHTML = `<span style="color: var(--danger-color);">Помилка: ${err.message}</span>`;
    }
};

window.closeSyncModal = function() {
    const modal = document.getElementById('sync-modal');
    if (modal) modal.style.display = 'none';
};

window.toggleSelectAll = function(type) {
    const master = document.getElementById(`select-all-${type}`);
    const checks = document.querySelectorAll(`.${type}-item-check`);
    checks.forEach(c => c.checked = master.checked);
};

window.applySyncSelection = async function() {
    if (!VaccinesUI.currentPreviewData) return;

    const selectedNew = [];
    document.querySelectorAll('.new-item-check:checked').forEach(c => {
        const idx = parseInt(c.dataset.index);
        selectedNew.push(VaccinesUI.currentPreviewData.newVaccines[idx]);
    });

    const selectedChanged = [];
    document.querySelectorAll('.changed-item-check:checked').forEach(c => {
        const idx = parseInt(c.dataset.index);
        selectedChanged.push(VaccinesUI.currentPreviewData.changedVaccines[idx].proposed);
    });

    if (selectedNew.length === 0 && selectedChanged.length === 0) {
        AdminAuth.showToast('Нічого не вибрано для застосування', 'warning');
        return;
    }

    try {
        await VaccinesApi.applySync({ toAdd: selectedNew, toUpdate: selectedChanged });
        AdminAuth.showToast('Зміни успішно застосовано!', 'success');
        window.closeSyncModal();
        await loadVaccinesData();
    } catch (err) {
        // Handled
    }
};

window.triggerBackfill = async function() {
    if (!confirm('Згенерувати графік вакцин для всіх існуючих дітей? Минулі вакцини отримають статус SKIPPED.')) {
        return;
    }

    try {
        const summary = await VaccinesApi.triggerBackfill();
        AdminAuth.showToast(`Успішно! Опрацьовано дітей: ${summary.totalChildren}. Створено подій: ${summary.createdEventsCount} (Пропущено: ${summary.skippedEventsCount}, Заплановано: ${summary.plannedEventsCount})`, 'success');
    } catch (err) {
        // Handled
    }
};

window.deleteVaccine = async function(id) {
    if (!confirm('Ви впевнені?')) return;
    try {
        await VaccinesApi.deleteVaccine(id);
        AdminAuth.showToast('Видалено', 'success');
        await loadVaccinesData();
    } catch (err) {
        // Handled
    }
};

window.editVaccine = async function(id) {
    try {
        const vaccines = await VaccinesApi.getVaccines();
        const v = vaccines.find(v => v.id === id);
        if (v) {
            document.getElementById('vaccine-id').value = v.id;
            document.getElementById('name').value = v.name;
            document.getElementById('disease').value = v.disease;
            document.getElementById('doseNumber').value = v.doseNumber;
            document.getElementById('totalDoses').value = v.totalDoses;
            document.getElementById('type').value = v.type;
            document.getElementById('scheduledAgeMonths').value = v.scheduledAgeMonths || '';
            document.getElementById('scheduledAgeDays').value = v.scheduledAgeDays || '';
            document.getElementById('notes').value = v.notes || '';

            document.getElementById('form-title').textContent = 'Редагувати щеплення';
            document.getElementById('submit-btn').textContent = 'Оновити щеплення';
            document.getElementById('cancel-btn').style.display = 'inline-block';
            window.scrollTo({ top: 0, behavior: 'smooth' });
        }
    } catch (err) {}
};

window.resetForm = function() {
    VaccinesUI.resetForm();
};

async function loadVaccinesData() {
    try {
        const vaccines = await VaccinesApi.getVaccines();
        VaccinesUI.renderVaccines(vaccines);
    } catch (err) {
        // Handled
    }
}

document.addEventListener('DOMContentLoaded', async () => {
    await loadVaccinesData();

    const vaccineForm = document.getElementById('vaccine-form');
    if (vaccineForm) {
        vaccineForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const id = document.getElementById('vaccine-id').value;
            const payload = {
                name: document.getElementById('name').value,
                disease: document.getElementById('disease').value,
                doseNumber: parseInt(document.getElementById('doseNumber').value),
                totalDoses: parseInt(document.getElementById('totalDoses').value),
                type: document.getElementById('type').value,
                scheduledAgeMonths: document.getElementById('scheduledAgeMonths').value ? parseInt(document.getElementById('scheduledAgeMonths').value) : null,
                scheduledAgeDays: document.getElementById('scheduledAgeDays').value ? parseInt(document.getElementById('scheduledAgeDays').value) : null,
                notes: document.getElementById('notes').value
            };

            try {
                if (id) {
                    await VaccinesApi.updateVaccine(id, payload);
                } else {
                    await VaccinesApi.createVaccine(payload);
                }

                AdminAuth.showToast('Збережено успішно!', 'success');
                VaccinesUI.resetForm();
                await loadVaccinesData();
            } catch (err) {
                // Handled
            }
        });
    }
});
