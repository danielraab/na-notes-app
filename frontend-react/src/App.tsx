import { Route, Routes } from 'react-router-dom';
import { Header } from './components/Header';
import { DashboardPage } from './pages/DashboardPage';
import { NoteEditorPage } from './pages/NoteEditorPage';
import { PublicNotePage } from './pages/PublicNotePage';

export default function App() {
  return (
    <>
      <Header />
      <main>
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/notes/new" element={<NoteEditorPage />} />
          <Route path="/notes/:id" element={<NoteEditorPage />} />
          <Route path="/shared/:token" element={<PublicNotePage />} />
        </Routes>
      </main>
    </>
  );
}
