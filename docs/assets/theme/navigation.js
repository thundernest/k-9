document.querySelectorAll('.sidebar-scrollbox .section a[href*="adr"]').forEach(el => {
    let textNodes = [...el.childNodes].filter(node => node.nodeType === Node.TEXT_NODE && node.nodeValue.trim().length > 0);

    if (textNodes.length > 0) {
        let textNode = textNodes[0]; // First text node (ignoring elements like <strong>)
        let text = textNode.nodeValue.trim();

        if (text.length >= 4) {
            let span = document.createElement("span");
            span.classList.add("number");
            span.textContent = text.substring(0, 4);

            textNode.nodeValue = text.substring(4); // Remove first 4 chars from original text node

            el.insertBefore(span, textNode); // Insert the styled first 4 characters before the rest
        }
    }
});
