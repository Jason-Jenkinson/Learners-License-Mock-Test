(() => {
  const layouts = document.querySelectorAll(".fixed-layout");
  const designWidth = 1920;
  const designHeight = 1080;

  const fitLayoutsToWindow = () => {
    const scale = Math.min(
      window.innerWidth / designWidth,
      window.innerHeight / designHeight,
    );
    const offsetX = (window.innerWidth - designWidth * scale) / 2;
    const offsetY = (window.innerHeight - designHeight * scale) / 2;

    layouts.forEach((layout) => {
      layout.style.left = `${offsetX}px`;
      layout.style.top = `${offsetY}px`;
      layout.style.transform = `scale(${scale})`;
    });
  };

  window.addEventListener("resize", fitLayoutsToWindow);
  fitLayoutsToWindow();
})();
