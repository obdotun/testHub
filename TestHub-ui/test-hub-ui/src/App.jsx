import React, { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import {ThemeProvider} from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import theme from './theme/theme';
import AppLayout from './components/AppLayout';
import Dashboard     from './pages/Dashboard';
import Projects      from './pages/Projects';
import ProjectDetail from './pages/ProjectDetail';
import Runs          from './pages/Runs';
import RunDetail     from './pages/RunDetail';
import { projectApi } from './api/client';

export default function App() {
  const [projects, setProjects] = useState([]);

  useEffect(() => {
    projectApi.getAll().then(setProjects).catch(() => {});
  }, []);

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <BrowserRouter>
        <AppLayout projects={projects}>
          <Routes>
            <Route path="/"             element={<Dashboard />} />
            <Route path="/projects"     element={<Projects onProjectsChange={setProjects} />} />
            <Route path="/projects/:id" element={<ProjectDetail />} />
            <Route path="/runs"         element={<Runs />} />
            <Route path="/runs/:id"     element={<RunDetail />} />
          </Routes>
        </AppLayout>
      </BrowserRouter>
    </ThemeProvider>
  );
}