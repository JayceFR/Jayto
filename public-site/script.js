document.addEventListener('DOMContentLoaded', () => {
    const downloadBtn = document.getElementById('downloadBtn');

    if (downloadBtn) {
        downloadBtn.addEventListener('click', () => {
            const span = downloadBtn.querySelector('span');
            const originalText = span.textContent;

            span.textContent = 'Downloading...';
            downloadBtn.style.opacity = '0.8';

            setTimeout(() => {
                span.textContent = originalText;
                downloadBtn.style.opacity = '1';
            }, 3000);
        });
    }
});
