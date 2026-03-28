declare module 'react-quill' {
  import React from 'react';

  export interface UnprivilegedEditor {
    getLength(): number;
    getText(index?: number, length?: number): string;
    getHTML(): string;
    getBounds(index: number, length?: number): any;
    getSelection(focus?: boolean): any;
    getContents(index?: number, length?: number): any;
  }

  export interface ComponentProps {
    id?: string;
    className?: string;
    theme?: string;
    style?: React.CSSProperties;
    readOnly?: boolean;
    value?: string | any;
    defaultValue?: string | any;
    placeholder?: string;
    tabIndex?: number;
    bounds?: string | HTMLElement;
    scrollingContainer?: string | HTMLElement;
    onChange?: (content: string, delta: any, source: any, editor: UnprivilegedEditor) => void;
    onChangeSelection?: (range: any, source: any, editor: UnprivilegedEditor) => void;
    onFocus?: (range: any, source: any, editor: UnprivilegedEditor) => void;
    onBlur?: (previousRange: any, source: any, editor: UnprivilegedEditor) => void;
    onKeyDown?: React.EventHandler<any>;
    onKeyPress?: React.EventHandler<any>;
    onKeyUp?: React.EventHandler<any>;
    modules?: any;
    formats?: string[];
    children?: React.ReactElement<any>;
  }

  export default class ReactQuill extends React.Component<ComponentProps> {
    focus(): void;
    blur(): void;
    getEditor(): any;
  }
}
