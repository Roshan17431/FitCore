import { X } from 'lucide-react';
import Button from './Button';

const Modal = ({ children, isOpen, onClose, title }) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/50 p-4 backdrop-blur-sm sm:items-center">
      <section className="w-full max-w-lg rounded-lg border border-slate-200 bg-white p-5 shadow-2xl dark:border-slate-800 dark:bg-slate-950">
        <div className="mb-4 flex items-center justify-between gap-4">
          <h2 className="text-lg font-bold text-slate-950 dark:text-white">{title}</h2>
          <Button aria-label="Close modal" icon={X} onClick={onClose} size="icon" variant="ghost" />
        </div>
        {children}
      </section>
    </div>
  );
};

export default Modal;

