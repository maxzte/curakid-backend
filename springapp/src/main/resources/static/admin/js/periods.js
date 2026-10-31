/**
 * Life Periods Domain & UI Controller
 */

// --- API Service Layer ---
const PeriodsApi = {
    getPeriods: () => ApiClient.get('/admin/quiz/life-periods'),
    createPeriod: (payload) => ApiClient.post('/admin/quiz/life-periods', payload),
    updatePeriod: (id, payload) => ApiClient.put(`/admin/quiz/life-periods/${id}`, payload)
};

// --- UI / Render Layer ---
const PeriodsUI = {
    periods: [],
    currentDescription: null,

    renderTable() {
        const tbody = document.getElementById('periods-table-body');
        if (!tbody) return;

        if (!this.periods || this.periods.length === 0) {
            tbody.innerHTML = '<tr><td colspan="4" style="text-align: center;">Періодів не знайдено</td></tr>';
            return;
        }

        tbody.innerHTML = this.periods.map(p => {
            const safeCode = AdminAuth.escapeHtml(p.code);
            const safeLabel = AdminAuth.escapeHtml(p.label);
            return `
            <tr>
                <td><span class="badge">${safeCode}</span></td>
                <td style="font-weight: 500;">${safeLabel}</td>
                <td>${p.minMonths} – ${p.maxMonths}</td>
                <td>
                    <button class="action-btn edit-btn" onclick="openEditModal('${p.id}')">Редагувати</button>
                </td>
            </tr>
        `}).join('');
    },

    openEditModal(id) {
        const p = this.periods.find(x => x.id === id);
        if (!p) return;

        document.getElementById('edit-id').value = p.id;
        document.getElementById('edit-code').value = p.code;
        document.getElementById('edit-label').value = p.label;
        document.getElementById('edit-minMonths').value = p.minMonths;
        document.getElementById('edit-maxMonths').value = p.maxMonths;

        let desc = { ua: { title: '', subtitle: '', milestones: { movement: '', speech: '', cognition: '' }, tips: [] } };
        if (p.description && typeof p.description === 'object') {
            desc = p.description;
        } else if (typeof p.description === 'string' && p.description.trim() !== '') {
            try {
                desc = JSON.parse(p.description);
            } catch (e) {
                console.error("Failed to parse description", e);
                desc.ua.subtitle = p.description;
            }
        }
        this.currentDescription = desc;

        const data = desc.ua || desc;
        document.getElementById('edit-title').value = data.title || '';
        document.getElementById('edit-subtitle').value = data.subtitle || '';
        document.getElementById('edit-milestone-movement').value = data.milestones?.movement || '';
        document.getElementById('edit-milestone-speech').value = data.milestones?.speech || '';
        document.getElementById('edit-milestone-cognition').value = data.milestones?.cognition || '';

        const tipsContainer = document.getElementById('edit-tips-list');
        tipsContainer.innerHTML = '';
        if (data.tips && Array.isArray(data.tips)) {
            data.tips.forEach(t => window.addTip('edit-tips-list', t));
        } else if (data.tip) {
            window.addTip('edit-tips-list', data.tip);
        }
        if (tipsContainer.children.length === 0) {
            window.addTip('edit-tips-list');
        }

        document.getElementById('edit-modal')?.classList.add('open');
    },

    closeModal() {
        document.getElementById('edit-modal')?.classList.remove('open');
    }
};

window.addTip = function(containerId, value = '') {
    const container = document.getElementById(containerId);
    if (!container) return;
    const div = document.createElement('div');
    div.className = 'tip-item';
    div.innerHTML = `
        <input type="text" class="form-control tip-input" placeholder="Порада..." value="${value}">
        <button type="button" class="btn remove-tip-btn" onclick="removeTip(this)">×</button>
    `;
    container.appendChild(div);
};

window.removeTip = function(btn) {
    if (btn && btn.parentElement) {
        btn.parentElement.remove();
    }
};

window.openEditModal = function(id) {
    PeriodsUI.openEditModal(id);
};

window.closeModal = function() {
    PeriodsUI.closeModal();
};

function collectTips(containerId) {
    const inputs = document.querySelectorAll(`#${containerId} .tip-input`);
    return Array.from(inputs).map(i => i.value).filter(v => v.trim() !== '');
}

function constructDescription(prefix) {
    const tips = collectTips(prefix + 'tips-list');
    return JSON.stringify({
        ua: {
            title: document.getElementById(prefix + 'title').value,
            subtitle: document.getElementById(prefix + 'subtitle').value,
            milestones: {
                movement: document.getElementById(prefix + 'milestone-movement').value,
                speech: document.getElementById(prefix + 'milestone-speech').value,
                cognition: document.getElementById(prefix + 'milestone-cognition').value
            },
            tips: tips
        }
    });
}

function constructEditDescription() {
    const base = PeriodsUI.currentDescription || { ua: {} };
    const ua = base.ua || base;
    const existingMilestones = ua.milestones || {};

    const title = document.getElementById('edit-title').value.trim();
    const subtitle = document.getElementById('edit-subtitle').value.trim();
    const movement = document.getElementById('edit-milestone-movement').value.trim();
    const speech = document.getElementById('edit-milestone-speech').value.trim();
    const cognition = document.getElementById('edit-milestone-cognition').value.trim();
    const tips = collectTips('edit-tips-list');

    if (!title && !subtitle && !movement && !speech && !cognition && tips.length === 0) {
        return undefined;
    }

    const merged = {
        ...ua,
        ...(title ? { title } : {}),
        ...(subtitle ? { subtitle } : {}),
        milestones: {
            ...existingMilestones,
            ...(movement ? { movement } : {}),
            ...(speech ? { speech } : {}),
            ...(cognition ? { cognition } : {})
        },
        ...(tips.length > 0 ? { tips } : {})
    };

    return JSON.stringify(base.ua ? { ...base, ua: merged } : merged);
}

async function loadPeriodsData() {
    try {
        PeriodsUI.periods = await PeriodsApi.getPeriods();
        PeriodsUI.renderTable();
    } catch (err) {
        // Handled
    }
}

document.addEventListener('DOMContentLoaded', async () => {
    await loadPeriodsData();

    const addPeriodForm = document.getElementById('add-period-form');
    if (addPeriodForm) {
        addPeriodForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const payload = {
                code: document.getElementById('code').value,
                label: document.getElementById('label').value,
                minMonths: parseInt(document.getElementById('minMonths').value),
                maxMonths: parseInt(document.getElementById('maxMonths').value),
                description: constructDescription('')
            };

            try {
                await PeriodsApi.createPeriod(payload);
                AdminAuth.showToast('Період успішно додано!', 'success');
                e.target.reset();
                document.getElementById('add-tips-list').innerHTML = '';
                window.addTip('add-tips-list');
                await loadPeriodsData();
            } catch (err) {
                // Handled
            }
        });
    }

    const editPeriodForm = document.getElementById('edit-period-form');
    if (editPeriodForm) {
        editPeriodForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const id = document.getElementById('edit-id').value;

            const payload = {};
            const code = document.getElementById('edit-code').value.trim();
            const label = document.getElementById('edit-label').value.trim();
            const minMonths = document.getElementById('edit-minMonths').value;
            const maxMonths = document.getElementById('edit-maxMonths').value;
            const description = constructEditDescription();

            if (code) payload.code = code;
            if (label) payload.label = label;
            if (minMonths !== '' && !isNaN(parseInt(minMonths))) payload.minMonths = parseInt(minMonths);
            if (maxMonths !== '' && !isNaN(parseInt(maxMonths))) payload.maxMonths = parseInt(maxMonths);
            if (description !== undefined) payload.description = description;

            try {
                await PeriodsApi.updatePeriod(id, payload);
                AdminAuth.showToast('Період оновлено!', 'success');
                PeriodsUI.closeModal();
                await loadPeriodsData();
            } catch (err) {
                // Handled
            }
        });
    }
});
