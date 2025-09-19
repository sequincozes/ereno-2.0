import React, { useState } from 'react';
import {
  useFloating,
  useClick,
  useDismiss,
  useRole,
  useInteractions,
  useId,
  FloatingOverlay,
  FloatingFocusManager,
  useTransitionStyles
} from '@floating-ui/react';

type ModalProps = {
  title: React.ReactNode;
  content: React.ReactNode;
};

export default function Modal({ title, content }: ModalProps) {  
  const [isOpen, setIsOpen] = useState(true);

  const { refs, context } = useFloating({
    open: isOpen,
    onOpenChange: setIsOpen
  });

  const { isMounted, styles } = useTransitionStyles(context, {
    duration: 200,
    initial: {
      opacity: 0,
      transform: 'translateY(50px)'
    }
  });

  const click = useClick(context);
  const dismiss = useDismiss(context, {
    outsidePressEvent: 'mousedown'
  });
  const role = useRole(context);

  const { getReferenceProps, getFloatingProps } = useInteractions([click, dismiss, role]);

  const labelId = useId();
  const descriptionId = useId();

  return (
        <FloatingOverlay
          lockScroll
          className="fixed top-0 left-0 right-0 bottom-0 z-[998] bg-surface-50/75 dark:bg-surface-950/75 backdrop-blur-sm flex justify-center items-center p-4"
        >
          <FloatingFocusManager context={context}>
            {/* Modal */}
            <div
              ref={refs.setFloating}
              aria-labelledby={labelId}
              aria-describedby={descriptionId}
              {...getFloatingProps()}
              className="card bg-surface-100-900 p-4 space-y-4 shadow-xl max-w-screen-sm"
              style={{
                ...styles // Transition styles
              }}
            >
              <header className="flex justify-between text-white">
                <h2 className="h2">{title}</h2>
              </header>
              <article>
                <p className="opacity-60 text-white">
                  {content}
                </p>
              </article>
              <footer className="flex justify-end gap-4">
                <button type="button" className="btn preset-filled" onClick={() => setIsOpen(false)}>
                  Confirm
                </button>
              </footer>
            </div>
          </FloatingFocusManager>
        </FloatingOverlay>
  );
};