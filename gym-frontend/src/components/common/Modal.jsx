import { useId } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import Button from './Button';
import { useDialog } from '../../hooks/useDialog';
import { cx } from '../../utils/formatters';

const Modal = ({ children, isOpen, onClose, title, drawer = false }) => {
  const titleId = useId();
  const panel = useDialog(isOpen, onClose);
  if (!isOpen) return null;
  return createPortal(
    <div data-dialog-root className={cx('fixed inset-0 z-50 flex bg-black/55 backdrop-blur-sm', drawer ? 'justify-start' : 'items-center justify-center p-4')} onClick={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <section ref={panel} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby={titleId} className={cx('border border-line bg-surface p-5 text-ink shadow-2xl outline-none', drawer ? 'flex h-dvh w-80 max-w-[90vw] flex-col overflow-y-auto' : 'max-h-[90dvh] w-full max-w-lg overflow-y-auto rounded-2xl')}>
        <div className="mb-5 flex items-center justify-between gap-4 border-b border-line pb-4">
          <h2 id={titleId} className="text-lg font-bold">{title}</h2>
          <Button aria-label={drawer ? 'Close navigation' : 'Close modal'} icon={X} onClick={onClose} size="icon" variant="ghost" />
        </div>
        {children}
      </section>
    </div>, document.body,
  );
};
export default Modal;
