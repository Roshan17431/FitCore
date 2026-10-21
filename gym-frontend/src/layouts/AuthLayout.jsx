import Brand from '../components/common/Brand';
import ThemeToggle from '../components/common/ThemeToggle';
import art from '../assets/strength-art.svg';

const AuthLayout = ({ children }) => (
  <div className="min-h-dvh bg-canvas text-ink">
    <a className="skip-link" href="#auth-content">Skip to form</a>
    <div className="grid min-h-dvh lg:grid-cols-[1fr_1fr]">
      <aside aria-label="About FitCore" className="relative hidden flex-col justify-between overflow-hidden bg-[#14251b] px-10 py-10 text-white lg:flex xl:px-16">
        <Brand to="/login" inverse />
        <div className="mx-auto w-full max-w-xl py-8">
          <p className="mb-5 text-xs font-semibold uppercase tracking-[.22em] text-emerald-200">Progress starts with consistency</p>
          <h2 className="max-w-lg text-5xl font-semibold leading-[1.1] xl:text-6xl">Build strength.<br /><span className="text-emerald-200">Stay consistent.</span></h2>
          <p className="mt-6 max-w-sm text-base leading-7 text-emerald-100/80">Your training, progress and gym essentials in one focused workspace.</p>
          <img alt="Illustration of a dumbbell on a training floor" src={art} width="640" height="440" className="mt-4 w-full" decoding="async" />
        </div>
        <p className="text-xs font-medium tracking-wide text-emerald-100/70">TRAIN WITH PURPOSE. TRACK YOUR PROGRESS.</p>
      </aside>
      <div className="flex min-w-0 flex-col">
        <header className="flex items-center justify-between gap-3 px-5 py-5 sm:px-10 lg:justify-end">
          <div className="lg:hidden"><Brand to="/login" /></div>
          <ThemeToggle />
        </header>
        <main id="auth-content" tabIndex={-1} className="mx-auto flex w-full max-w-xl flex-1 items-center px-5 py-6 outline-none sm:px-10 lg:py-12">{children}</main>
        <footer className="px-5 py-6 text-center text-xs text-muted">FitCore · A little stronger, every day.</footer>
      </div>
    </div>
  </div>
);
export default AuthLayout;
