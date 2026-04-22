function evaluatePassword(password) {
    let score = 0;
    if (password.length >= 8) score += 1;
    if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score += 1;
    if (/\d/.test(password)) score += 1;
    if (/[^A-Za-z0-9]/.test(password) || password.length >= 12) score += 1;

    if (!password.length) {
        return { percent: 0, label: "Enter a password" };
    }
    if (score <= 1) {
        return { percent: 25, label: "Weak" };
    }
    if (score === 2) {
        return { percent: 50, label: "Fair" };
    }
    if (score === 3) {
        return { percent: 75, label: "Strong" };
    }
    return { percent: 100, label: "Very strong" };
}

export function bindPasswordStrength(input, fillElement, labelElement) {
    if (!input || !fillElement || !labelElement) {
        return;
    }

    const render = () => {
        const { percent, label } = evaluatePassword(input.value);
        fillElement.style.width = `${percent}%`;
        labelElement.textContent = label;
    };

    input.addEventListener("input", render);
    render();
}
